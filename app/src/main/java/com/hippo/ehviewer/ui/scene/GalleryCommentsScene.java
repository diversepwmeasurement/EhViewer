/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.scene;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.hippo.anani.AnimationUtils;
import com.hippo.anani.SimpleAnimatorListener;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.LinearDividerItemDecoration;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryComment;
import com.hippo.rippleold.RippleSalon;
import com.hippo.scene.SceneFragment;
import com.hippo.scene.StageActivity;
import com.hippo.text.Html;
import com.hippo.text.URLImageGetter;
import com.hippo.util.ActivityHelper;
import com.hippo.util.ReadableTime;
import com.hippo.util.TextUrl;
import com.hippo.vector.VectorDrawable;
import com.hippo.view.ViewTransition;
import com.hippo.widget.LinkifyTextView;
import com.hippo.widget.SimpleImageView;
import com.hippo.yorozuya.LayoutUtils;

public final class GalleryCommentsScene extends ToolbarScene
        implements EasyRecyclerView.OnItemClickListener,
        EasyRecyclerView.OnItemLongClickListener, View.OnClickListener {

    public static final String KEY_GID = "gid";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_COMMENTS = "comments";

    private int mGid;
    private String mToken;
    private GalleryComment[] mComments;

    private FloatingActionButton mFab;
    private View mEditPanel;
    private SimpleImageView mSendImage;
    private EditText mEditText;

    private CommentAdapter mAdapter;
    private ViewTransition mViewTransition;

    private boolean mInAnimation = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private void handleArgs(Bundle args) {
        if (args == null) {
            return;
        }

        mGid = args.getInt(KEY_GID, -1);
        mToken = args.getString(KEY_TOKEN, null);
        Parcelable[] parcelables = args.getParcelableArray(KEY_COMMENTS);
        if (parcelables instanceof GalleryComment[]) {
            mComments = (GalleryComment[]) parcelables;
        }
    }

    private void onInit() {
        handleArgs(getArguments());
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mGid = savedInstanceState.getInt(KEY_GID, -1);
        mToken = savedInstanceState.getString(KEY_TOKEN, null);
        Parcelable[] parcelables = savedInstanceState.getParcelableArray(KEY_COMMENTS);
        if (parcelables instanceof GalleryComment[]) {
            mComments = (GalleryComment[]) parcelables;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_GID, mGid);
        outState.putString(KEY_TOKEN, mToken);
        outState.putParcelableArray(KEY_COMMENTS, mComments);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_gallery_comments, container, false);
        EasyRecyclerView recyclerView = (EasyRecyclerView) view.findViewById(R.id.recycler_view);
        View tip = view.findViewById(R.id.tip);
        SimpleImageView tipImage = (SimpleImageView) tip.findViewById(R.id.tip_image);
        mEditPanel = view.findViewById(R.id.edit_panel);
        mSendImage = (SimpleImageView) mEditPanel.findViewById(R.id.send);
        mEditText = (EditText) mEditPanel.findViewById(R.id.edit_text);
        mFab = (FloatingActionButton) view.findViewById(R.id.fab);

        mAdapter = new CommentAdapter();
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false));
        LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
                LinearDividerItemDecoration.VERTICAL, getResources().getColor(R.color.divider),
                LayoutUtils.dp2pix(getContext(), 1));
        decoration.setShowLastDivider(true);
        recyclerView.addItemDecoration(decoration);
        recyclerView.setSelector(RippleSalon.generateRippleDrawable(false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setOnItemClickListener(this);
        recyclerView.setOnItemLongClickListener(this);

        tipImage.setDrawable(VectorDrawable.create(getContext(), R.xml.sadpanda_head));

        mSendImage.setDrawable(VectorDrawable.create(getContext(), R.xml.ic_send_dark_x24));
        mSendImage.setOnClickListener(this);

        mFab.setImageDrawable(VectorDrawable.create(getContext(), R.xml.ic_reply_dark_x24));
        mFab.setOnClickListener(this);

        mViewTransition = new ViewTransition(recyclerView, tip);

        if (mComments == null || mComments.length <= 0) {
            mViewTransition.showView(1);
        } else {
            mViewTransition.showView(0);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAdapter = null;
        mViewTransition = null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.gallery_comments);
        setNavigationIcon(VectorDrawable.create(getContext(), R.xml.ic_arrow_left_dark_x24));

        // Clear nav checked item
        setNavCheckedItem(0);

        // Hide IME
        ActivityHelper.hideSoftInput(getActivity());
    }

    @Override
    public void onNavigationClick() {
        finish();
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
        if (holder instanceof CommentHolder) {
            CommentHolder commentHolder = (CommentHolder) holder;
            ClickableSpan span = commentHolder.comment.getCurrentSpan();
            commentHolder.comment.clearCurrentSpan();

            if (span instanceof URLSpan) {
                UrlOpener.openUrl(getActivity(), ((URLSpan) span).getURL(), true, true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onItemLongClick(EasyRecyclerView parent, View view, int position, long id) {
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showEditPanelWithAnimationL() {
        mInAnimation = true;
        mFab.setTranslationX(0.0f);
        mFab.setTranslationY(0.0f);
        mFab.setScaleX(1.0f);
        mFab.setScaleY(1.0f);
        int fabEndX = mEditPanel.getLeft() + (mEditPanel.getWidth() / 2) - (mFab.getWidth() / 2);
        int fabEndY = mEditPanel.getTop() + (mEditPanel.getHeight() / 2) - (mFab.getHeight() / 2);
        mFab.animate().x(fabEndX).y(fabEndY).scaleX(0.0f).scaleY(0.0f)
                .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                .setDuration(300L).setListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFab.setVisibility(View.INVISIBLE);
                mEditPanel.setVisibility(View.VISIBLE);
                int halfW = mEditPanel.getWidth() / 2;
                int halfH = mEditPanel.getHeight() / 2;
                Animator animator = ViewAnimationUtils.createCircularReveal(mEditPanel, halfW, halfH, 0,
                        (float) Math.hypot(halfW, halfH)).setDuration(300);
                animator.addListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mInAnimation = false;
                    }
                });
                animator.start();
            }
        }).start();
    }

    private void showEditPanel(boolean animation) {
        if (animation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                showEditPanelWithAnimationL();
            } else {
                // TODO Add animation for pre-L
                mFab.setVisibility(View.INVISIBLE);
                mEditPanel.setVisibility(View.VISIBLE);
            }
        } else {
            mFab.setVisibility(View.INVISIBLE);
            mEditPanel.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void hideEditPanelWithAnimationL() {
        mInAnimation = true;
        int halfW = mEditPanel.getWidth() / 2;
        int halfH = mEditPanel.getHeight() / 2;
        Animator animator = ViewAnimationUtils.createCircularReveal(mEditPanel, halfW, halfH,
                (float) Math.hypot(halfW, halfH), 0.0f).setDuration(300L);
        animator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mEditPanel.setVisibility(View.GONE);
                mFab.setVisibility(View.VISIBLE);
                int fabStartX = mEditPanel.getLeft() + (mEditPanel.getWidth() / 2) - (mFab.getWidth() / 2);
                int fabStartY = mEditPanel.getTop() + (mEditPanel.getHeight() / 2) - (mFab.getHeight() / 2);
                mFab.setX(fabStartX);
                mFab.setY(fabStartY);
                mFab.setScaleX(0.0f);
                mFab.setScaleY(0.0f);
                mFab.animate().translationX(0.0f).translationY(0.0f).scaleX(1.0f).scaleY(1.0f)
                        .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                        .setDuration(300L).setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mInAnimation = false;
                    }
                }).start();
            }
        });
        animator.start();
    }

    private void hideEditPanel(boolean animation) {
        if (animation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                hideEditPanelWithAnimationL();
            } else {
                // TODO Add animation for pre-L
                mFab.setVisibility(View.VISIBLE);
                mEditPanel.setVisibility(View.INVISIBLE);
            }
        } else {
            mFab.setVisibility(View.VISIBLE);
            mEditPanel.setVisibility(View.INVISIBLE);
        }
    }

    @Nullable
    private String getGalleryDetailUrl() {
        if (mGid != -1 && mToken != null) {
            return EhUrl.getGalleryDetailUrl(mGid, mToken, 0, true);
        } else {
            return null;
        }
    }

    @Override
    public void onClick(View v) {
        if (mFab == v) {
            if (!mInAnimation) {
                showEditPanel(true);
            }
        } else if (mSendImage == v) {
            if (!mInAnimation) {
                String comment = mEditText.getText().toString();
                if (TextUtils.isEmpty(comment)) {
                    // Comment is empty
                    return;
                }
                String url = getGalleryDetailUrl();
                if (url == null) {
                    return;
                }
                // Request
                EhRequest request = new EhRequest()
                        .setMethod(EhClient.METHOD_GET_COMMENT_GALLERY)
                        .setArgs(url, comment)
                        .setCallback(new CommentGalleryListener(getContext(),
                                ((StageActivity) getActivity()).getStageId(), getTag()));
                EhApplication.getEhClient(getContext()).execute(request);
                ActivityHelper.hideSoftInput(getActivity());
                hideEditPanel(true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mInAnimation) {
            return;
        }
        if (mEditPanel.getVisibility() == View.VISIBLE) {
            hideEditPanel(true);
        } else {
            finish();
        }
    }

    private class CommentHolder extends RecyclerView.ViewHolder {

        public TextView user;
        public TextView time;
        public LinkifyTextView comment;

        public CommentHolder(View itemView) {
            super(itemView);
            user = (TextView) itemView.findViewById(R.id.user);
            time = (TextView) itemView.findViewById(R.id.time);
            comment = (LinkifyTextView) itemView.findViewById(R.id.comment);
        }
    }

    private class CommentAdapter extends RecyclerView.Adapter<CommentHolder> {

        @Override
        public CommentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new CommentHolder(getActivity().getLayoutInflater()
                    .inflate(R.layout.item_gallery_comment, parent, false));
        }

        @Override
        public void onBindViewHolder(CommentHolder holder, int position) {
            GalleryComment comment = mComments[position];
            holder.user.setText(comment.user);
            holder.time.setText(ReadableTime.getTimeAgo(comment.time));
            holder.comment.setText(TextUrl.handleTextUrl(Html.fromHtml(comment.comment,
                    new URLImageGetter(holder.comment, EhApplication.getConaco(getContext())), null)));
        }

        @Override
        public int getItemCount() {
            return mComments == null ? 0 : mComments.length;
        }
    }

    private void onCommentGallerySuccess(GalleryComment[] result) {
        mComments = result;
        mAdapter.notifyDataSetChanged();
        Bundle re = new Bundle();
        re.putParcelableArray(KEY_COMMENTS, result);
        setResult(SceneFragment.RESULT_OK, re);
    }

    private static class CommentGalleryListener implements EhClient.Callback<GalleryComment[]> {

        private EhApplication mApplication;
        private int mStageId;
        private String mSceneTag;

        public CommentGalleryListener(Context context, int stageId, String sceneTag) {
            mApplication = (EhApplication) context.getApplicationContext();
            mStageId = stageId;
            mSceneTag = sceneTag;
        }

        private GalleryCommentsScene getScene() {
            StageActivity stage = mApplication.findStageActivityById(mStageId);
            if (stage == null) {
                return null;
            }
            SceneFragment scene = stage.findSceneByTag(mSceneTag);
            if (scene instanceof GalleryCommentsScene) {
                return (GalleryCommentsScene) scene;
            } else {
                return null;
            }
        }

        @Override
        public void onSuccess(GalleryComment[] result) {
            // Show toast
            Toast.makeText(mApplication, R.string.comment_successfully, Toast.LENGTH_SHORT).show();

            GalleryCommentsScene scene = getScene();
            if (scene != null) {
                scene.onCommentGallerySuccess(result);
            }
        }

        @Override
        public void onFailure(Exception e) {
            // Show toast
            Toast.makeText(mApplication, R.string.comment_failed, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
        }
    }
}
