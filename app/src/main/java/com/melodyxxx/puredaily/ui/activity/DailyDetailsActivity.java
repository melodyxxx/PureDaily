package com.melodyxxx.puredaily.ui.activity;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.NestedScrollView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.melodyxxx.puredaily.R;
import com.melodyxxx.puredaily.constant.PrefConstants;
import com.melodyxxx.puredaily.dao.CollectionManager;
import com.melodyxxx.puredaily.entity.bmob.BmobCollection;
import com.melodyxxx.puredaily.entity.bmob.BmobUser;
import com.melodyxxx.puredaily.entity.daily.Collection;
import com.melodyxxx.puredaily.entity.daily.NewsDetails;
import com.melodyxxx.puredaily.utils.Blur;
import com.melodyxxx.puredaily.utils.L;
import com.melodyxxx.puredaily.utils.PrefUtils;
import com.melodyxxx.puredaily.utils.SnackBarUtils;
import com.melodyxxx.puredaily.utils.Tip;
import com.melodyxxx.puredaily.widget.LoadingDialog;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;
import rx.Subscriber;
import rx.Subscription;

/**
 * Author:      Melodyxxx
 * Email:       95hanjie@gmail.com
 * Created at:  2016/6/1.
 * Description:
 */
public class DailyDetailsActivity extends SubscriptionActivity implements NestedScrollView.OnScrollChangeListener, AppBarLayout.OnOffsetChangedListener {

    @BindView(R.id.iv_image)
    ImageView mImage;

    @BindView(R.id.iv_blur)
    ImageView mBlurImage;

    @BindView(R.id.web_view)
    WebView mWebView;

    @BindView(R.id.collapsing_toolbar)
    CollapsingToolbarLayout mCollapsingToolbarLayout;

    @BindView(R.id.app_bar)
    AppBarLayout mAppBarLayout;

    @BindView(R.id.tv_image_source)
    TextView mImageSource;

    @BindView(R.id.fab_collect)
    FloatingActionButton mCommentFab;

    @BindView(R.id.fab_go_to_top)
    FloatingActionButton mGoToTopFab;

    @BindView(R.id.nested_scrollview)
    NestedScrollView mNestedScrollView;

    private int mId;

    private NewsDetails mNewsDetails;
    private LoadingDialog mLoadingDialog = LoadingDialog.create();

    @Override
    public int getContentView() {
        return R.layout.activity_latest_details;
    }

    @Override
    public int getStatusBarOptions() {
        return StatusBarOptions.LAYOUT_FULLSCREEN_STATUS_BAR;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCollapsingToolbarLayout.setTitle("");
        mCollapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.ExpandedAppBar);
        mCollapsingToolbarLayout.setCollapsedTitleTextAppearance(R.style.DarkCollapsedAppBar);
        mId = getIntent().getIntExtra("id", 0);
        initCommentFab();
        initListener();
        initWebView();
        getDetailsData();
        hideGoToTopFab();
    }

    private void initCommentFab() {
        // 初始化收藏菜单状态
        boolean isCollected = CollectionManager.isCollected(mId);
        mCommentFab.setImageResource(isCollected ? R.drawable.ic_collected : R.drawable.ic_collect);
    }

    @OnClick(R.id.fab_collect)
    public void onCommentFabClick() {
        if (!hasAccount()) {
            showLoginServiceTipDialog();
            return;
        }

        final long time = System.currentTimeMillis();
        final BmobCollection bmobCollection = new BmobCollection(getCurrentUserName(), mId, mNewsDetails.title, mNewsDetails.images[0], time);

        boolean isCollected = CollectionManager.isCollected(mId);
        if (isCollected) {
            mLoadingDialog.showWith(getSupportFragmentManager(), "请稍后");
            // 查询objectId
            BmobQuery<BmobCollection> query = new BmobQuery<>();
            query.addWhereEqualTo("name", getCurrentUserName());
            query.addWhereEqualTo("id", mId);
            query.setLimit(1);
            query.findObjects(new FindListener<BmobCollection>() {
                @Override
                public void done(List<BmobCollection> object, BmobException e) {
                    if (e == null) {
                        if (object.size() == 0) {
                            // 没有数据
                            mLoadingDialog.dismiss();
                            return;
                        }
                        // 查询到数据
                        BmobCollection collection = new BmobCollection();
                        collection.setObjectId(object.get(0).getObjectId());
                        collection.delete(new UpdateListener() {
                            @Override
                            public void done(BmobException e) {
                                mLoadingDialog.dismiss();
                                if (e == null) {
                                    Tip.with(DailyDetailsActivity.this).onSuccess("取消收藏成功");
                                    deleteLocalCollection();
                                } else {
                                    Tip.with(DailyDetailsActivity.this).onNotice("取消收藏失败" + e.getMessage());
                                }
                            }
                        });
                    } else {
                        mLoadingDialog.dismiss();
                    }
                }
            });
        } else {
            mLoadingDialog.showWith(getSupportFragmentManager(), "正在收藏");
            bmobCollection.save(new SaveListener<String>() {
                @Override
                public void done(String s, BmobException e) {
                    mLoadingDialog.dismiss();
                    if (e == null) {
                        Tip.with(DailyDetailsActivity.this).onSuccess("收藏成功");
                        collectToLocal(time);
                    } else {
                        Tip.with(DailyDetailsActivity.this).onNotice("服务器异常:" + e.getMessage());
                    }
                }
            });
        }
    }

    private void deleteLocalCollection() {
        CollectionManager.deleteById(mId);
        mCommentFab.setImageResource(R.drawable.ic_collect);
        SnackBarUtils.makeShort(this, mWebView, getString(R.string.tip_cancel_collect)).show();
    }

    private void collectToLocal(long time) {
        Collection collection = new Collection(
                mId,
                mNewsDetails.title,
                mNewsDetails.images[0],
                time
        );
        CollectionManager.insert(collection);
        mCommentFab.setImageResource(R.drawable.ic_collected);
        SnackBarUtils.makeShort(this, mWebView, getString(R.string.tip_collected)).show();
    }

    private void hideGoToTopFab() {
        mNestedScrollView.post(new Runnable() {
            @Override
            public void run() {
                mGoToTopFab.hide();
            }
        });
    }

    private void initListener() {
        mAppBarLayout.addOnOffsetChangedListener(this);
        mNestedScrollView.setOnScrollChangeListener(this);
    }

    private void getDetailsData() {
        Subscription subscription = mDailyApiManager.getDetails(mId)
                .subscribe(new Subscriber<NewsDetails>() {

                    @Override
                    public void onNext(NewsDetails newsDetails) {
                        onGetSuccess(newsDetails);
                        onGetDone();
                    }

                    @Override
                    public void onError(Throwable e) {
                        onGetFailed(e.getMessage());
                        onGetDone();
                    }

                    @Override
                    public void onCompleted() {
                        onGetDone();
                    }

                });
        mCompositeSubscription.add(subscription);
    }

    @OnClick(R.id.fab_go_to_top)
    public void onFabToTopClick() {
        mNestedScrollView.smoothScrollTo(0, 0);
    }

    private void onGetSuccess(NewsDetails newsDetails) {
        this.mNewsDetails = newsDetails;
        if (!isFinishing()) {
            mImageSource.setText(mNewsDetails.image_source);
            mCollapsingToolbarLayout.setTitle(mNewsDetails.title);
            if (!PrefUtils.getBoolean(this, PrefConstants.MODE_NO_PIC, false)) {
                Glide.with(DailyDetailsActivity.this)
                        .load(mNewsDetails.image != null ? mNewsDetails.image : mNewsDetails.images[0])
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontTransform()
                        .dontAnimate()
                        .into(new GlideDrawableImageViewTarget(mImage) {
                            @Override
                            public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> animation) {
                                super.onResourceReady(resource, animation);
                                readyBlur();
                            }
                        });
            } else {
                mImage.setImageDrawable(new ColorDrawable(Color.BLACK));
                mBlurImage.setImageDrawable(new ColorDrawable(Color.BLACK));
            }
            if (TextUtils.isEmpty(mNewsDetails.body)) {
                mWebView.loadUrl(mNewsDetails.share_url);
                return;
            }
            String body = mNewsDetails.body.replace("<div class=\"headline\">", "").replace("<div class=\"img-place-holder\">", "");
            String htmlData = "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\" />" + "<br/>" + body;
            mWebView.loadDataWithBaseURL("file:///android_asset/", htmlData, "text/html", "UTF-8", null);
        }
    }

    private void readyBlur() {
        mImage.postDelayed(new Runnable() {
            @Override
            public void run() {
                Blur blur = new Blur(DailyDetailsActivity.this, mImage, mBlurImage);
                blur.blur(32, 25);
            }
        }, 400);
    }

    private void onGetFailed(String errorMsg) {
        SnackBarUtils.makeShort(DailyDetailsActivity.this, mWebView, errorMsg).show();
    }

    private void onGetDone() {

    }

    private void initWebView() {
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setBuiltInZoomControls(false);
        // 是否加载图片
        mWebView.getSettings().setBlockNetworkImage(PrefUtils.getBoolean(this, PrefConstants.MODE_NO_PIC, false));
    }

    public static void start(Activity activity, int id, View view) {
        Intent intent = new Intent(activity, DailyDetailsActivity.class);
        intent.putExtra("id", id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !PrefUtils.getBoolean(activity, PrefConstants.MODE_NO_PIC, false)) {
            // Android 5.0+ && 没有开启无图模式 开启共享元素动画
            activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(activity, view, activity.getString(R.string.transition_latest_with_latest_details)).toBundle());
        } else {
            activity.startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_daily_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share: {
                shareStory();
                break;
            }
            case R.id.action_comment: {
                CommentActivity.startCommentActivity(this, mId);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareStory() {
        if (mNewsDetails == null) {
            return;
        }
        // 构建分享内容
        StringBuilder sb = new StringBuilder();
        sb.append("分享来自「Pure Daily」：");
        sb.append(mNewsDetails.share_url);
        sb.append(" （");
        sb.append(mNewsDetails.title);
        sb.append("）");

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.dialog_title_share)));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mWebView.setVisibility(View.INVISIBLE);
        mImage.setAlpha(1f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebView.destroy();
    }

    @Override
    public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        if ((oldScrollY - scrollY) > ViewConfiguration.get(this).getScaledTouchSlop()) {
            mGoToTopFab.show();
        } else if ((scrollY - oldScrollY > ViewConfiguration.get(this).getScaledTouchSlop())) {
            mGoToTopFab.hide();
        }
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        float totalScrollRange = appBarLayout.getTotalScrollRange();
        mImage.setAlpha(Math.abs(verticalOffset + totalScrollRange) / totalScrollRange);
        if (verticalOffset != 0) {
            // 展开状态
            mGoToTopFab.hide();
        }
    }

}
