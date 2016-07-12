package com.ttwishing.horizontallistview;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.Scroller;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 应用于listview中item中包含的ListView,不可与其他object的ListView共用此类
 */
public class HorizontalListView extends AdapterView<ListAdapter> {

    //TODO(lining) 与其他item中的HorizontalListView共享回收机制
    private static Queue<View> sRemovedViewQueue = new LinkedList();

    //是否支持手势滑动
    private final boolean supportScorll = true;
    //是否支持惯性滑动
    private final boolean suuportFling = true;

//    private Queue<View> sRemovedViewQueue = new LinkedList();

    private boolean isScrolling = false;

    private int mLeftViewIndex = -1;
    private int mRightViewIndex = 0;
    protected int mCurrentX;
    protected int mNextX;
    private int mMaxX = Integer.MAX_VALUE;
    private int mDisplayOffset = 0;

    private GestureDetector mGestureDetector;

    private boolean mDataChanged = false;


    private final Runnable requestLayoutRunnable = new Runnable() {

        @Override
        public void run() {
            requestLayout();
        }
    };

    private OnItemSelectedListener onItemSelectedListener;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;


    protected ListAdapter mAdapter;
    protected Scroller mScroller;

    public HorizontalListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        this.mScroller = new Scroller(context);
        this.mGestureDetector = new GestureDetector(context, this.onGestureListener);
        initView();
    }

    public boolean isScrolling() {
        return this.isScrolling;
    }

    private synchronized void initView() {
        this.mLeftViewIndex = -1;
        this.mRightViewIndex = 0;
        this.mDisplayOffset = 0;
        this.mCurrentX = 0;
        this.mNextX = 0;
        this.mMaxX = Integer.MAX_VALUE;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mAdapter == null)
            return;

        if (mDataChanged) {
            int oldCurrentX = this.mCurrentX;
            initView();
            clearViews();
            mNextX = oldCurrentX;
        }

        if (mScroller.computeScrollOffset()) {
            mNextX = mScroller.getCurrX();
        }

        if (mNextX <= 0) {
            mNextX = 0;
            mScroller.forceFinished(true);
        }

        if (mNextX > mMaxX) {
            mNextX = mMaxX;
            mScroller.forceFinished(true);
        }

        int dx = mCurrentX - mNextX;
        removeNonVisibleItems(dx);
        fillList(dx);
        positionItems(dx);

        mCurrentX = mNextX;
        if (!mScroller.isFinished()) {
            post(requestLayoutRunnable);
        }
    }

    private void removeNonVisibleItems(int dx) {
        int size = getChildCount();
        if (size < 1) {
            return;
        }
        View child = getChildAt(0);
        while (child != null && dx + child.getRight() <= 0) {
            this.mDisplayOffset += child.getMeasuredWidth();
            this.sRemovedViewQueue.offer(child);
            removeViewInLayout(child);
            size--;
            this.mLeftViewIndex++;
            if (size > 0) {
                child = getChildAt(0);
            } else {
                child = null;
            }
        }

        if (size >= 1) {
            child = getChildAt(size - 1);
            while (child != null && dx + child.getLeft() >= getWidth()) {
                this.sRemovedViewQueue.offer(child);
                removeViewInLayout(child);
                size--;
                this.mRightViewIndex--;
                if (size > 0) {
                    child = getChildAt(-1 + getChildCount());
                } else {
                    child = null;
                }
            }
        }
    }

    private void fillList(int dx) {
        int edge = 0;
        View child = getChildAt(-1 + getChildCount());
        if (child != null) {
            edge = child.getRight();
        }
        fillListRight(edge, dx);

        child = getChildAt(0);
        edge = 0;
        if (child != null) {
            edge = child.getLeft();
        }
        fillListLeft(edge, dx);
    }

    private void fillListRight(int rightEdge, int dx) {
        while (rightEdge + dx < getWidth() && this.mRightViewIndex < this.mAdapter.getCount()) {
            View child = this.mAdapter.getView(this.mRightViewIndex, sRemovedViewQueue.poll(), this);
            addAndMeasureChild(child, -1);

            rightEdge += child.getMeasuredWidth();

            if (this.mRightViewIndex == this.mAdapter.getCount() - 1) {
                this.mMaxX = rightEdge + this.mCurrentX - getWidth();
            }
            if (this.mMaxX < 0) {
                this.mMaxX = 0;
            }
            this.mRightViewIndex++;
        }
    }

    private void fillListLeft(int leftEdge, int dx) {
        while (leftEdge + dx > 0 && this.mLeftViewIndex >= 0) {
            View child = this.mAdapter.getView(this.mLeftViewIndex, sRemovedViewQueue.poll(), this);
            addAndMeasureChild(child, 0);
            leftEdge -= child.getMeasuredWidth();
            this.mLeftViewIndex--;
            this.mDisplayOffset -= child.getMeasuredWidth();
        }
    }

    private void addAndMeasureChild(View child, int viewPos) {
        LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(-1, -1);
        }
        addViewInLayout(child, viewPos, params, true);

        int defaultSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        int childWidthSpec;
        if (params.width > 0) {
            childWidthSpec = MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY);
        } else {
            childWidthSpec = defaultSpec;
        }

        int childHeightSpec;
        if (params.height > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = defaultSpec;
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private void positionItems(int dx) {
        int childCount = getChildCount();

        if (childCount > 0) {

            mDisplayOffset += dx;
            int left = mDisplayOffset;

            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                int childWidth = child.getMeasuredWidth();

                child.layout(left, 0, left + childWidth, child.getMeasuredHeight());

                left += childWidth;
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if(!suuportFling){
            if ((ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) && this.isScrolling) {
                this.isScrolling = false;
                return true;
            }
        }

        return super.dispatchTouchEvent(ev) | mGestureDetector.onTouchEvent(ev);
    }

    protected boolean onDown(MotionEvent e) {
        this.mScroller.forceFinished(true);

        if(!supportScorll){
            //当return false时，将不会再接收后续的事件，这样listview就是不可滑动的
            return false;
        }

        return true;
    }

    protected boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        synchronized (this) {
            this.mScroller.fling(this.mNextX, 0, (int) -velocityX, 0, 0, this.mMaxX, 0, 0);
        }
        requestLayout();
        return true;
    }

    protected boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        ViewParent parent = getParent();
        if (parent != null) {
            //当接收到此事件后，执行此方法可使父view不拦截此事件，使listview继续滑动，http://blog.csdn.net/chaihuasong/article/details/17499799
            parent.requestDisallowInterceptTouchEvent(true);
        }
        synchronized (this) {
            this.mNextX += (int) distanceX;
        }

        requestLayout();
        this.isScrolling = true;
        return true;
    }

    @Override
    public ListAdapter getAdapter() {
        return this.mAdapter;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (this.mAdapter != null) {
            this.mAdapter.unregisterDataSetObserver(this.dataSetObserver);
        }
        this.mAdapter = adapter;
        this.mAdapter.registerDataSetObserver(this.dataSetObserver);
        reset();
    }

    private synchronized void reset() {
        this.mScroller.forceFinished(true);
        clearViews();
        requestLayout();
    }

    public void clearViews() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            sRemovedViewQueue.offer(child);
        }
        removeAllViewsInLayout();
        initView();
    }

    public static void clearRemovedViewsCache() {
        sRemovedViewQueue.clear();
    }

    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setSelection(int position) {
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }

    @Override
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.onItemSelectedListener = listener;
    }


    private DataSetObserver dataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            synchronized (HorizontalListView.this) {
                mDataChanged = true;
                invalidate();
                requestLayout();
            }
        }

        @Override
        public void onInvalidated() {
            reset();
            invalidate();
            requestLayout();
        }
    };

    private final OnGestureListener onGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            return HorizontalListView.this.onDown(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return HorizontalListView.this.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return HorizontalListView.this.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            for (int j = 0; j < getChildCount(); j++) {
                View child = getChildAt(j);
                if (!isMotionEventInView(e, child)) {
                    continue;
                }
                if (onItemLongClickListener != null) {
                    onItemLongClickListener.onItemLongClick(HorizontalListView.this, child, j + (1 + mLeftViewIndex), mAdapter.getItemId(j + (1 + mLeftViewIndex)));
                }
            }
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            boolean bool = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (isMotionEventInView(e, child)) {
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(HorizontalListView.this, child, i + (1 + mLeftViewIndex), mAdapter.getItemId(i + (1 + mLeftViewIndex)));
                    }
                    if (onItemSelectedListener != null) {
                        onItemSelectedListener.onItemSelected(HorizontalListView.this, child, i + (1 + mLeftViewIndex), mAdapter.getItemId(i + (1 + mLeftViewIndex)));
                    }
                    bool = true;
                }
            }
            return bool;
        }

        private boolean isMotionEventInView(MotionEvent motionEvent, View childView) {
            Rect rect = new Rect();
            int[] location = new int[2];
            childView.getLocationOnScreen(location);
            int left = location[0];
            int top = location[1];
            rect.set(left, top, left + childView.getWidth(), top + childView.getHeight());
            return rect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY());
        }
    };
}
