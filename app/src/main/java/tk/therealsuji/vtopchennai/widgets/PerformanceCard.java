package tk.therealsuji.vtopchennai.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.animations.AlphaAnimation;
import tk.therealsuji.vtopchennai.animations.ResizeAnimation;

public class PerformanceCard extends LinearLayout {
    AppCompatTextView title, scoreText;
    Context context;
    ProgressBar scoreProgress;

    JSONObject defaultValues;

    public PerformanceCard(Context context) {
        super(context);

        this.context = context;
        this.initialize();
    }

    public PerformanceCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        this.title = new AppCompatTextView(context);
        this.scoreText = new AppCompatTextView(context);
        this.scoreProgress = new ProgressBar(context, null, 0, R.style.Widget_VTOP_CircularProgressBar);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PerformanceCard, 0, 0);

        try {
            this.setTitle(a.getString(R.styleable.PerformanceCard_title));
            this.setScore(
                    (double) a.getFloat(R.styleable.PerformanceCard_score, 0),
                    (double) a.getFloat(R.styleable.PerformanceCard_total, 100)
            );
        } finally {
            a.recycle();
        }

        this.context = context;
        this.initialize();
    }

    private void initialize() {
        float pixelDensity = this.context.getResources().getDisplayMetrics().density;
        this.defaultValues = new JSONObject();

        TypedValue colorPrimary = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorPrimary, colorPrimary, true);

        LayoutParams titleParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(
                0,
                0,
                0,
                (int) (10 * pixelDensity)
        );
        this.title.setLayoutParams(titleParams);
        this.title.setTextColor(colorPrimary.data);
        this.title.setTextSize(15);

        this.addView(this.title);

        /*
            This empty view is used to fill blank space
         */
        View emptyView = new RelativeLayout(this.context);
        LayoutParams outerContainerParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                0,
                1
        );
        emptyView.setLayoutParams(outerContainerParams);

        this.addView(emptyView);

        RelativeLayout container = new RelativeLayout(this.context);
        LayoutParams containerParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        container.setLayoutParams(containerParams);

        RelativeLayout.LayoutParams scoreProgressParams = new RelativeLayout.LayoutParams(
                (int) (100 * pixelDensity),
                (int) (100 * pixelDensity)
        );
        this.scoreProgress.setIndeterminateDrawable(ContextCompat.getDrawable(this.getContext(), R.drawable.background_circular_progress_indeterminate));
        this.scoreProgress.setLayoutParams(scoreProgressParams);

        RelativeLayout.LayoutParams scoreTextParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        scoreTextParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        this.scoreText.setLayoutParams(scoreTextParams);
        this.scoreText.setTextColor(colorPrimary.data);
        this.scoreText.setTextSize(17);
        this.scoreText.setTypeface(this.scoreText.getTypeface(), Typeface.BOLD);

        container.addView(this.scoreProgress);
        container.addView(this.scoreText);
        this.addView(container);

        this.setBackground(ContextCompat.getDrawable(this.context, R.drawable.background_card));
        this.setOrientation(VERTICAL);
        this.setPadding(
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity),
                (int) (20 * pixelDensity)
        );
    }

    public void setTitle(String title) {
        this.title.setText(title);
    }

    public void setScore(Double score, Double max) {
        score = Math.ceil(score);
        max = Math.ceil(max);

        this.scoreProgress.setProgress(score.intValue(), true);
        this.scoreProgress.setSecondaryProgress(max.intValue());

        String scoreText = (max >= 100) ? score.intValue() + "%" : score.intValue() + "/" + max.intValue();
        this.scoreText.setText(scoreText);
    }

    public void setIndeterminate(boolean indeterminate) {
        this.scoreProgress.setIndeterminate(indeterminate);

        if (indeterminate) {
            this.scoreText.setVisibility(INVISIBLE);
        } else {
            this.scoreText.setVisibility(VISIBLE);
        }
    }

    private void ensureDefaultValues() {
        if (!this.defaultValues.has("cardWidth")) {
            float pixelDensity = getContext().getResources().getDisplayMetrics().density;
            int width = getMeasuredWidth();
            if (width <= 0 && getLayoutParams() != null) {
                width = getLayoutParams().width;
            }
            if (width <= 0) {
                width = (int) (140 * pixelDensity);
            }
            int marginStart = (int) (10 * pixelDensity);
            int marginEnd = (int) (10 * pixelDensity);
            if (getLayoutParams() instanceof LayoutParams) {
                LayoutParams mlp = (LayoutParams) getLayoutParams();
                if (mlp.getMarginStart() > 0) marginStart = mlp.getMarginStart();
                if (mlp.getMarginEnd() > 0) marginEnd = mlp.getMarginEnd();
            }
            try {
                this.defaultValues.put("cardWidth", width);
                this.defaultValues.put("marginStart", marginStart);
                this.defaultValues.put("marginEnd", marginEnd);
            } catch (Exception ignored) {
            }
        }
    }

    public void show() {
        this.clearAnimation();
        this.post(() -> {
            try {
                ensureDefaultValues();
                LinearLayout card = this;
                int cardWidth = this.defaultValues.optInt("cardWidth", (int) (140 * getContext().getResources().getDisplayMetrics().density));
                if (cardWidth <= 0) {
                    cardWidth = (int) (140 * getContext().getResources().getDisplayMetrics().density);
                }

                ResizeAnimation expandAnimation = new ResizeAnimation(card, ResizeAnimation.DIRECTION_X, cardWidth, this.defaultValues);
                AlphaAnimation alphaAnimation = new AlphaAnimation(card, 1);

                expandAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        card.startAnimation(alphaAnimation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });

                card.setVisibility(VISIBLE);
                card.startAnimation(expandAnimation);
            } catch (Exception ignored) {
                this.setVisibility(VISIBLE);
                this.setAlpha(1f);
            }
        });
    }

    public void hide() {
        this.clearAnimation();
        this.post(() -> {
            try {
                ensureDefaultValues();
                LinearLayout card = this;

                ResizeAnimation compressAnimation = new ResizeAnimation(card, ResizeAnimation.DIRECTION_X, 0, this.defaultValues);
                AlphaAnimation alphaAnimation = new AlphaAnimation(card, 0);

                alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        card.setVisibility(INVISIBLE);
                        card.startAnimation(compressAnimation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });

                card.startAnimation(alphaAnimation);
            } catch (Exception ignored) {
                this.setVisibility(INVISIBLE);
            }

        });
    }
}
