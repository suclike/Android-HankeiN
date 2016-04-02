package com.github.gfx.hankei_n.fragment;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.github.gfx.hankei_n.HankeiNApplication;
import com.github.gfx.hankei_n.activity.MainActivity;
import com.github.gfx.hankei_n.databinding.CardLocationMemoBinding;
import com.github.gfx.hankei_n.databinding.FragmentSidemenuBinding;
import com.github.gfx.hankei_n.event.LocationMemoChangedEvent;
import com.github.gfx.hankei_n.event.LocationMemoFocusedEvent;
import com.github.gfx.hankei_n.event.LocationMemoRemovedEvent;
import com.github.gfx.hankei_n.model.LocationMemo;
import com.github.gfx.hankei_n.model.LocationMemoManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import rx.functions.Action1;
import rx.subjects.BehaviorSubject;
import timber.log.Timber;

@ParametersAreNonnullByDefault
public class SidemenuFragment extends Fragment {

    static final String TAG = SidemenuFragment.class.getSimpleName();

    Adapter adapter;

    @Inject
    BehaviorSubject<LocationMemoChangedEvent> locationMemoChangedSubject;

    @Inject
    BehaviorSubject<LocationMemoRemovedEvent> locationMemoRemovedSubject;

    @Inject
    BehaviorSubject<LocationMemoFocusedEvent> locationMemoFocusedSubject;

    @Inject
    Tracker tracker;

    @Inject
    Vibrator vibrator;

    @Inject
    LocationMemoManager memos;

    FragmentSidemenuBinding binding;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        HankeiNApplication.getAppComponent(context).inject(this);

        Timber.d("onAttach");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSidemenuBinding.inflate(inflater, container, false);

        adapter = new Adapter(inflater); // it depends on memos

        binding.listLocationMemos.setAdapter(adapter);

        binding.buttonAddLocationMemo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditLocationMemoFragment.newInstance()
                        .show(getFragmentManager(), "edit_location_memo");
            }
        });

        locationMemoChangedSubject.subscribe(new Action1<LocationMemoChangedEvent>() {
            @Override
            public void call(LocationMemoChangedEvent changedEvent) {
                adapter.notifyDataSetChanged();
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void showEditDialog(LocationMemo memo) {
        vibrator.vibrate(100);

        tracker.send(new HitBuilders.EventBuilder()
                .setCategory(MainActivity.CATEGORY_LOCATION_MEMO)
                .setAction("showEditDialog")
                .setLabel(TAG)
                .build());

        EditLocationMemoFragment.newInstance(memo)
                .show(getFragmentManager(), "edit_location_memo");
    }

    private void focusOnMemo(LocationMemo memo) {
        locationMemoFocusedSubject.onNext(new LocationMemoFocusedEvent(memo));
    }

    private static class VH extends RecyclerView.ViewHolder {

        final CardLocationMemoBinding binding;

        public VH(CardLocationMemoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private class Adapter extends RecyclerView.Adapter<VH> {

        final LayoutInflater inflater;

        public Adapter(LayoutInflater inflater) {
            this.inflater = inflater;
        }

        public LocationMemo getItem(int position) {
            return memos.get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return memos.count();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            CardLocationMemoBinding binding = CardLocationMemoBinding.inflate(inflater, parent, false);
            return new VH(binding);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            final LocationMemo memo = getItem(position);

            Timber.d("onBindViewHolder for " + memo.address);

            CardLocationMemoBinding binding = holder.binding;
            binding.circle.setImageDrawable(createCircle(hueToColor(memo.markerHue)));
            binding.textAddress.setText(memo.address);
            binding.textNote.setText(memo.note);

            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    focusOnMemo(memo);
                }
            });
            binding.getRoot().setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showEditDialog(memo);
                    return true;
                }
            });
        }

        @ColorInt
        int hueToColor(float hue) {
            return Color.HSVToColor(new float[]{hue, 1.0f, 1.0f});
        }

        Drawable createCircle(@ColorInt int color) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(dpToPx(1), darker(color, 0.7f));
            return drawable;
        }

        public int dpToPx(int dp) {
            return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
        }

        public int darker(int color, @FloatRange(from = 0.0, to = 1.0) float factor) {
            int a = Color.alpha(color);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            return Color.argb(a,
                    Math.max((int) (r * factor), 0),
                    Math.max((int) (g * factor), 0),
                    Math.max((int) (b * factor), 0));
        }
    }
}
