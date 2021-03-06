package com.github.gfx.hankei_n.fragment;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.maps.model.LatLng;

import com.cookpad.android.rxt4a.schedulers.AndroidSchedulers;
import com.github.gfx.hankei_n.R;
import com.github.gfx.hankei_n.databinding.DialogEditLocationMemoBinding;
import com.github.gfx.hankei_n.dependency.DependencyContainer;
import com.github.gfx.hankei_n.event.LocationMemoAddedEvent;
import com.github.gfx.hankei_n.event.LocationMemoRemovedEvent;
import com.github.gfx.hankei_n.model.AddressAutocompleAdapter;
import com.github.gfx.hankei_n.model.LocationMemo;
import com.github.gfx.hankei_n.model.LocationMemoManager;
import com.github.gfx.hankei_n.model.PlaceEngine;
import com.github.gfx.hankei_n.toolbox.Assets;
import com.github.gfx.hankei_n.toolbox.Intents;
import com.github.gfx.hankei_n.toolbox.Locations;
import com.github.gfx.hankei_n.toolbox.MarkerHueAllocator;
import com.jakewharton.rxbinding.widget.RxTextView;
import com.jakewharton.rxbinding.widget.TextViewAfterTextChangeEvent;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import java.util.Locale;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import timber.log.Timber;

@ParametersAreNonnullByDefault
public class EditLocationMemoFragment extends BottomSheetDialogFragment {

    static final String TAG = EditLocationMemoFragment.class.getSimpleName();

    static final String kLocationMemo = "location_memo";

    @Inject
    LocationMemoManager memos;

    @Inject
    MarkerHueAllocator markerHueAllocator;

    @Inject
    PlaceEngine placeEngine;

    @Inject
    Assets assets;

    @Inject
    DisplayMetrics displayMetrics;

    @Inject
    Locale locale;

    @Inject
    Tracker tracker;

    @Inject
    PublishSubject<LocationMemoAddedEvent> locationMemoAddedSubject;

    @Inject
    PublishSubject<LocationMemoRemovedEvent> locationMemoRemovedSubject;

    DialogEditLocationMemoBinding binding;

    LocationMemo argMemo;

    LocationMemo memo;

    AddressAutocompleAdapter adapter;

    String initialAddress;

    boolean doNotSaveOnPause;

    public static EditLocationMemoFragment newInstance() {
        EditLocationMemoFragment fragment = new EditLocationMemoFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    public static EditLocationMemoFragment newInstance(LocationMemo memo) {
        EditLocationMemoFragment fragment = new EditLocationMemoFragment();

        Bundle args = new Bundle();
        args.putSerializable(kLocationMemo, memo);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        DependencyContainer.getComponent(this).inject(this);
    }

    @Override
    public void setupDialog(Dialog dialog, int style) {
        final long t0 = System.currentTimeMillis();
        super.setupDialog(dialog, style);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        binding = DialogEditLocationMemoBinding.inflate(inflater);
        bindData(dialog, (LocationMemo) getArguments().getSerializable(kLocationMemo), t0);
        dialog.setContentView(binding.getRoot());
    }

    public void bindData(@NonNull Dialog dialog, @Nullable LocationMemo argMemo, final long t0) {
        if (argMemo != null) {
            memo = argMemo.copy();
            this.argMemo = argMemo;
            initialAddress = memo.address;

            if (memo.address.isEmpty() && memo.isPointingSomewhere()) {
                placeEngine.getAddressFromLocation(memo.getLatLng())
                        .retry(1)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<String>() {
                            @Override
                            public void call(String s) {
                                initAddress(s);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                Timber.w(throwable, "no address found");
                            }
                        });
            } else if (!memo.address.isEmpty() && memo.isPointingNowhere()) {
                Timber.w("No location set in %s", memo);
            }

        } else {
            memo = memos.newMemo(getContext(), markerHueAllocator, Locations.NOWHERE);
            this.argMemo = memo.copy();

        }
        binding.setMemo(memo);
        binding.setFragment(this);

        binding.iconCircle.setImageDrawable(assets.createMarkerDrawable(memo.markerHue));

        adapter = new AddressAutocompleAdapter(getContext(), placeEngine);

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                View contentView = binding.getRoot();
                BottomSheetBehavior.from((View) contentView.getParent()).setPeekHeight(contentView.getHeight());

                binding.editAddress.setAdapter(adapter);

                // it must be done after initial binding
                setupEventListeners();

                long elapsed = System.currentTimeMillis() - t0;
                tracker.send(new HitBuilders.TimingBuilder()
                        .setCategory(TAG)
                        .setVariable("setupDialog")
                        .setValue(elapsed)
                        .build());
                Timber.d("setupDialog: %dms", elapsed);
            }
        });
    }

    void setupEventListeners() {

        RxTextView.afterTextChangeEvents(binding.editAddress).subscribe(new Action1<TextViewAfterTextChangeEvent>() {
            @Override
            public void call(TextViewAfterTextChangeEvent event) {
                Editable s = event.editable();
                memo.address = s.toString();
                updateButtons();
            }
        });

        RxTextView.afterTextChangeEvents(binding.editNote).subscribe(new Action1<TextViewAfterTextChangeEvent>() {
            @Override
            public void call(TextViewAfterTextChangeEvent event) {
                Editable s = event.editable();
                memo.note = s.toString();
            }
        });

        RxTextView.afterTextChangeEvents(binding.editRadius).subscribe(new Action1<TextViewAfterTextChangeEvent>() {
            @Override
            public void call(TextViewAfterTextChangeEvent event) {
                Editable s = event.editable();
                memo.radius = parseDouble(s);
                updateButtons();
            }
        });

        binding.checkboxCircle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.editRadius.setEnabled(isChecked);
                memo.drawCircle = isChecked;
                updateButtons();
            }
        });
    }

    void updateButtons() {
        binding.delete.setEnabled(!memo.address.isEmpty());
        binding.streetView.setEnabled(!memo.address.isEmpty());
        binding.map.setEnabled(!memo.address.isEmpty());
        binding.share.setEnabled(!memo.address.isEmpty());
    }

    double parseDouble(CharSequence s) {
        try {
            return Double.parseDouble(s.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        placeEngine.start();
    }

    @Override
    public void onPause() {
        if (!doNotSaveOnPause) {
            saveLocationMemoAddedEventAndDismiss();
        }
        placeEngine.stop();

        super.onPause();
    }

    boolean shouldSkipGeocoding() {
        return memo.address.equals(initialAddress) || memo.isPointingSomewhere();
    }

    public void saveLocationMemoAddedEventAndDismiss() {
        if (memo.address.isEmpty()) {
            Timber.d("saveLocationMemoAddedEventAndDismiss: empty address");
            dismiss();
            return;
        }
        if (argMemo.contentEquals(memo)) {
            Timber.d("saveLocationMemoAddedEventAndDismiss: no changes");
            dismiss();
            return;
        }
        if (shouldSkipGeocoding()) {
            castLocationMemoAndDismiss();
            return;
        }

        placeEngine.getLocationFromAddress(memo.address)
                .retry(1)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<LatLng>() {
                    @Override
                    public void call(LatLng latLng) {
                        memo.setLatLng(latLng);
                        castLocationMemoAndDismiss();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Timber.w(throwable, "failed to getLocationFromAddress: %s", memo.address);
                        castLocationMemoAndDismiss();
                    }
                });
    }

    void castLocationMemoAndDismiss() {
        locationMemoAddedSubject.onNext(new LocationMemoAddedEvent(memo));
        dismissAllowingStateLoss();
    }

    public void askToRemove(View view) {
        if (!memo.isPersistent()) {
            doNotSaveOnPause = true;
            dismiss();
            return;
        }

        new AlertDialog.Builder(getActivity())
                .setIcon(assets.createMarkerDrawable(memo.markerHue))
                .setTitle(R.string.ask_to_remove_memo)
                .setMessage(memo.address)
                .setPositiveButton(R.string.affirmative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeMemo();
                    }
                })
                .setNegativeButton(R.string.negative, null)
                .show();
    }

    void removeMemo() {
        doNotSaveOnPause = true;
        locationMemoRemovedSubject.onNext(new LocationMemoRemovedEvent(memo));
        dismiss();
    }

    public void openWithStreetView(View view) {
        if (shouldSkipGeocoding()) {
            startActivity(Intents.createStreetViewIntent(memo.latitude, memo.longitude));
        } else {
            placeEngine.getLocationFromAddress(memo.address)
                    .retry(1)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<LatLng>() {
                        @Override
                        public void call(LatLng latLng) {
                            memo.setLatLng(latLng);
                            startActivity(Intents.createStreetViewIntent(memo.latitude, memo.longitude));
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Timber.w(throwable, "failed to getLocationFromAddress: %s", memo.address);
                            showErrorWithToast("No network connected. Retry it.");
                        }
                    });
        }

        tracker.send(new HitBuilders.EventBuilder()
                .setCategory(TAG)
                .setAction("openWithStreetView")
                .build());
    }

    public void openWithMap(View view) {
        if (shouldSkipGeocoding()) {
            startActivity(Intents.createOpenWithMapIntent(memo));
        } else {
            placeEngine.getLocationFromAddress(memo.address)
                    .retry(1)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<LatLng>() {
                        @Override
                        public void call(LatLng latLng) {
                            memo.setLatLng(latLng);
                            startActivity(Intents.createOpenWithMapIntent(memo));
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Timber.w(throwable, "failed to getLocationFromAddress: %s", memo.address);
                            showErrorWithToast("No network connected. Retry it.");
                        }
                    });
        }

        tracker.send(new HitBuilders.EventBuilder()
                .setCategory(TAG)
                .setAction("openWithMap")
                .build());
    }

    public void openShareChooser(View view) {
        startActivity(Intents.createShareLocationMemoIntent(getContext(), memo));
        tracker.send(new HitBuilders.EventBuilder()
                .setCategory(TAG)
                .setAction("openShareChooser")
                .build());
    }

    public void initAddress(String address) {
        if (binding.editAddress.getText().length() == 0) {
            binding.editAddress.setAdapter(null);
            binding.editAddress.setText(address);
            binding.editAddress.setAdapter(adapter);
            initialAddress = address;
        }
    }

    void showErrorWithToast(String message) {
        if (isAdded()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}
