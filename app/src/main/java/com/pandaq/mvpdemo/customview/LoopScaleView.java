package com.pandaq.mvpdemo.customview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import com.pandaq.mvpdemo.R;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by PandaQ on 2017/1/13.
 * email : 767807368@qq.com
 * 一个循环的刻度尺
 */

public class LoopScaleView extends View {
    private final static String TAG = "com.pandaq.mvpdemo";
    //画底线的画笔
    private Paint paint;
    //控件显示宽度
    private int showWidth;
    //尺子控件总宽度
    private float viewWidth;
    //尺子控件总宽度
    private float viewHeight;
    //中间的标识图片
    private Bitmap cursorMap;
    //标签的位置
    private float cursorLocation;
    //未设置标识图片时默认绘制一条线作为标尺的线的颜色
    private int cursorColor = Color.RED;
    //大刻度线宽，默认为3
    private int cursorWidth = 3;
    //小刻度线宽，默认为2
    private int scaleWidth = 2;
    //设置屏幕宽度内最多显示的大刻度数，默认为6个
    private int showItemSize = 6;
    //标尺开始位置
    private float currLocation = 0;
    //刻度表的最大值，默认为250
    private int maxValue = 200;
    //一个刻度表示的值的大小
    private int oneItemValue = 1;
    //设置刻度线间宽度,大小由 showItemSize确定
    private int scaleDistance;
    //刻度高度，默认值为40
    private float scaleHeight = 40;
    //已选择的刻度区间的刻度色
    private int scaleSelectColor = Color.BLUE;
    //未选择的区间的刻度色
    private int scaleUnSelectColor = Color.GRAY;
    //刻度文字的颜色
    private int scaleTextColor = Color.BLUE;
    //刻度文字的大小,默认为24px
    private int scaleTextSize = 24;
    //手势解析器
    private GestureDetector mGestureDetector;
    //处理惯性滚动
    private Scroller mScroller;
    //惯性滑动时用于查询位置状态
    private static ScheduledExecutorService mScheduler;
    //scroller 滚动的最大值
    private int maxX;
    //scroller 滚动的最小值
    private int minX;

    public LoopScaleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LoopScaleView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LoopScaleView);
        showItemSize = ta.getInteger(R.styleable.LoopScaleView_maxShowItem, 3);
        ta.recycle();
        mScroller = new Scroller(context);
        mScheduler = Executors.newScheduledThreadPool(2);
        mGestureDetector = new GestureDetector(context, gestureListener);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);
        showWidth = getMeasuredWidth();
        //一个小刻度的宽度（十进制，每5个小刻度为一个大刻度）
        scaleDistance = (showWidth / (showItemSize * 5));
        //尺子长度总的个数*一个的宽度
        viewWidth = maxValue / oneItemValue * scaleDistance;
        maxX = getItemsCount() * scaleDistance;
        minX = -maxX;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.clipRect(getPaddingStart(), getPaddingTop(), getWidth() - getPaddingRight(), viewHeight - getPaddingBottom());
        drawLine(canvas);
        drawCursor(canvas);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(scaleWidth);
        for (int i = 0; i < maxValue / oneItemValue; i++) {
            drawScale(canvas, i, -1);
        }
        for (int i = 0; i < maxValue / oneItemValue; i++) {
            drawScale(canvas, i, 1);
        }
    }

    /**
     * 绘制主线
     *
     * @param canvas 绘制的画布
     */
    private void drawLine(Canvas canvas) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(3);
        paint.setColor(scaleUnSelectColor);
        canvas.drawLine(getPaddingStart(), viewHeight - getPaddingBottom(), viewWidth - getPaddingEnd(), viewHeight - getPaddingBottom(), paint);
    }

    /**
     * 绘制指示标签
     *
     * @param canvas
     */
    private void drawCursor(Canvas canvas) {
        if (cursorMap == null) { //绘制一条红色的竖线线
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStrokeWidth(cursorWidth);
            paint.setColor(cursorColor);
            cursorLocation = showItemSize / 2 * 5 * scaleDistance; //屏幕显示Item 数的中间位置
            canvas.drawLine(cursorLocation, getPaddingTop() - getPaddingBottom(), cursorLocation, viewHeight - getPaddingBottom(), paint);
        } else { //绘制标识图片
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            float left = (showWidth - cursorMap.getWidth()) / 2;
            float top = getPaddingTop();
            float right = (showWidth + cursorMap.getWidth()) / 2;
            float bottom = 2 * viewHeight / 5 - getPaddingBottom();
            RectF rectF = new RectF(left, top, right, bottom);
            canvas.drawBitmap(cursorMap, null, rectF, paint);
        }
    }


    /**
     * 绘制刻度线
     *
     * @param canvas 画布
     * @param value  刻度值
     * @param type   正向绘制还是逆向绘制
     */
    private void drawScale(Canvas canvas, int value, int type) {
        if (currLocation + showItemSize / 2 * 5 * scaleDistance >= viewWidth) {
            currLocation = -showItemSize / 2 * 5 * scaleDistance;
        } else if (currLocation - showItemSize / 2 * 5 * scaleDistance <= -viewWidth) {
            currLocation = showItemSize / 2 * 5 * scaleDistance;
        }
        float location = cursorLocation - currLocation + value * scaleDistance * type;
        if (value % 10 == 0) {
            canvas.drawLine(location, viewHeight - scaleHeight - getPaddingBottom(), location, viewHeight - getPaddingBottom(), paint);
            Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintText.setTextSize(scaleTextSize);
            if (type < 0) {
                value = maxValue - value;
                if (value == maxValue) { //左闭右开区间，不取最大值
                    value = 0;
                }
            }
            String drawStr = String.valueOf(value);
            Rect bounds = new Rect();
            paintText.getTextBounds(drawStr, 0, drawStr.length(), bounds);
            canvas.drawText(drawStr, location - bounds.width() / 2, viewHeight - (scaleHeight + 5) - getPaddingBottom(), paintText);
        } else {
            canvas.drawLine(location, viewHeight - scaleHeight / 2 - getPaddingBottom(), location, viewHeight - getPaddingBottom(), paint);
        }
    }

    // 拦截屏幕滑动事件
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i(TAG, "action_dpwn");
                break;
            case MotionEvent.ACTION_UP:
                Log.i(TAG, "action_up");
                //手指抬起是计算出当前滑到第几个位置
                getIntegerPosition();
                break;
        }
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    /**
     * 滑动手势处理
     */
    private GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            scrollView(distanceX);
            return true;
        }

        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mScroller.fling((int) currLocation, 0, (int) (-velocityX / 1.5), 0, minX, maxX, 0, 0);
//            //每100毫秒检查一次状态，使用Scheduler 有BUG 暂时先用handler解决问题
//            mScheduler.scheduleAtFixedRate(locationChecker, 100, 100, TimeUnit.MILLISECONDS);
            setNextMessage(0);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return super.onSingleTapUp(e);
        }
    };

    /**
     * 实时通知惯性滚动的状态
     */
    private Runnable locationChecker = new Runnable() {
        @Override
        public void run() {
            System.out.println("别闹执行任务呢");
            if (!mScroller.isFinished()) { //如果滑动尚未结束，则不断得到新的位置并更新视图
                System.out.println("可以执行任务");
                mScroller.computeScrollOffset();
                int currX = mScroller.getCurrX();
                float delta = currLocation - currX;
                currLocation = currX;
                if (delta != 0) {
                    System.out.println(delta);
                    scrollView(delta);
                }
            } else {
                mScheduler.shutdownNow();
            }
        }
    };


    /**
     * 滑动View
     *
     * @param distance 滑动的距离
     */
    private void scrollView(float distance) {
        currLocation += distance;
        //设置新的位置
        setCurrLocation(currLocation);
    }

    private void getIntegerPosition() {
        int currentItem = (int) (currLocation / scaleDistance);
        float fraction = currLocation - currentItem * currentItem;
        if (fraction > 0.5 * scaleDistance) {
            currLocation = (currentItem + 1) * scaleDistance;
        } else {
            currLocation = currentItem * scaleDistance;
        }
        invalidate();
    }

    /**
     * 获取一共有多少个刻度
     *
     * @return
     */
    public int getItemsCount() {
        return maxValue / oneItemValue;
    }

    /**
     * 设置标识的颜色
     *
     * @param color 颜色id
     */
    public void setCursorColor(int color) {
        this.cursorColor = color;
        invalidate();
    }

    /**
     * 设置标识的宽度
     *
     * @param width 宽度
     */
    public void setCursorWidth(int width) {
        this.cursorWidth = width;
        invalidate();
    }

    /**
     * 设置游标的bitmap位图
     *
     * @param cursorMap 位图
     */
    public void setCursorMap(Bitmap cursorMap) {
        this.cursorMap = cursorMap;
        invalidate();
    }

    /**
     * 设置刻度线的宽度
     *
     * @param scaleWidth 刻度线的宽度
     */
    public void setScaleWidth(int scaleWidth) {
        this.scaleWidth = scaleWidth;
        invalidate();
    }

    /**
     * 设置屏幕宽度内大Item的数量
     *
     * @param showItemSize 屏幕宽度内显示的大 item数量
     */
    public void setShowItemSize(int showItemSize) {
        this.showItemSize = showItemSize;
        invalidate();
    }

    /**
     * 设置当前游标所在的值
     *
     * @param currLocation 当前游标所在的值
     */
    public void setCurrLocation(float currLocation) {
        this.currLocation = currLocation;
        invalidate();
    }

    /**
     * 设置刻度线的高度
     *
     * @param scaleHeight
     */
    public void setScaleHeight(float scaleHeight) {
        this.scaleHeight = scaleHeight;
        invalidate();
    }

    /**
     * 设置已选择区域的背景颜色
     *
     * @param scaleSelectColor 已选区域的背景色
     */
    public void setScaleSelectColor(int scaleSelectColor) {
        this.scaleSelectColor = scaleSelectColor;
        invalidate();
    }

    /**
     * 设置未选择区域的背景颜色
     *
     * @param scaleUnSelectColor 未选区域的背景色
     */
    public void setScaleUnSelectColor(int scaleUnSelectColor) {
        this.scaleUnSelectColor = scaleUnSelectColor;
        invalidate();
    }

    /**
     * 设置刻度表上文字的颜色
     *
     * @param scaleTextColor 文字颜色id
     */
    public void setScaleTextColor(int scaleTextColor) {
        this.scaleTextColor = scaleTextColor;
        invalidate();
    }

    /**
     * 设置刻度标上的文字的大小
     *
     * @param scaleTextSize 文字大小
     */
    public void setScaleTextSize(int scaleTextSize) {
        this.scaleTextSize = scaleTextSize;
        invalidate();
    }

    /**
     * 设置刻度的最大值
     *
     * @param maxValue 刻度的最大值
     */
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        invalidate();
    }

    /**
     * 设置 一刻度所代表的的值的大小
     *
     * @param oneItemValue
     */
    public void setOneItemValue(int oneItemValue) {
        this.oneItemValue = oneItemValue;
        invalidate();
    }


    private void setNextMessage(int message) {
        animationHandler.removeMessages(0);
        animationHandler.sendEmptyMessage(message);
    }

    // 动画处理
    private Handler animationHandler = new Handler() {
        public void handleMessage(Message msg) {
            mScroller.computeScrollOffset();
            int currX = mScroller.getCurrX();
            float delta = currX - currLocation;
            if (delta != 0) {
                scrollView(delta);
            }
            // 滚动还没有完成，到最后，完成手动
            if (!mScroller.isFinished()) {
                animationHandler.sendEmptyMessage(msg.what);
            } else {
                //到整数刻度
                getIntegerPosition();
            }
        }
    };
}