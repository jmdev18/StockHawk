package com.udacity.stockhawk.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.udacity.stockhawk.R;
import com.udacity.stockhawk.data.Contract;
import com.udacity.stockhawk.data.PrefUtils;
import com.udacity.stockhawk.sync.QuoteSyncJob;
import com.udacity.stockhawk.utils.BasicUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>,
        SwipeRefreshLayout.OnRefreshListener,
        StockAdapter.StockAdapterOnClickHandler,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int STOCK_LOADER = 0;
    @BindView(R.id.recycler_view)
    RecyclerView stockRecyclerView;
    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.error)
    TextView error;
    @BindView(R.id.activity_main)
    public CoordinatorLayout activityMainLayout;
    private StockAdapter adapter;
    private Snackbar snackbar;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BasicUtils.isNetworkUp(context)) {
                showInternetOffSnackBar();
            } else {
                swipeRefreshLayout.setRefreshing(true);
                if (snackbar != null) snackbar.dismiss();
                updateEmptyView();
            }
        }
    };

    @Override
    public void onClick(String symbol, StockAdapter.StockViewHolder viewHolder) {
        Uri stockUri = Contract.Quote.makeUriForStock(symbol);
        Intent intent = new Intent(this, StockDetailActivity.class);
        intent.setData(stockUri);
        Pair<View, String> priceViewPair = Pair.create((View) viewHolder.price, getString(R.string.stock_price_transition_name));
        Pair<View, String> changeViewPair = Pair.create((View) viewHolder.change, getString(R.string.stock_change_transition_name));
        ActivityOptionsCompat optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, priceViewPair, changeViewPair);

        ActivityCompat.startActivity(this, intent, optionsCompat.toBundle());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportPostponeEnterTransition();
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        adapter = new StockAdapter(this, this);
        stockRecyclerView.setAdapter(adapter);
        stockRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        swipeRefreshLayout.setOnRefreshListener(this);
        if (savedInstanceState == null) {
            QuoteSyncJob.initialize(this);
            swipeRefreshLayout.setRefreshing(true);
        }
        setUpDeletionOnSlide();
        getSupportLoaderManager().initLoader(STOCK_LOADER, null, this);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        registerReceiver(broadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onRefresh() {

        if (BasicUtils.isNetworkUp(this)) {
            QuoteSyncJob.syncImmediately(this);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setRefreshing(true);
        } else {
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.cancelLongPress();
            showInternetOffSnackBar();
            hideMessage();
        }
    }

    private void setUpDeletionOnSlide() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                String symbol = adapter.getSymbolAtPosition(viewHolder.getAdapterPosition());
                int stockSize = PrefUtils.removeStock(MainActivity.this, symbol);
                int x = getContentResolver().delete(Contract.Quote.makeUriForStock(symbol), null, null);
                QuoteSyncJob.updateWidget(MainActivity.this);
                if (stockSize == 0) {
                    adapter.setCursor(null);
                    updateEmptyView();
                }
            }
        }).attachToRecyclerView(stockRecyclerView);
    }

    public void button(@SuppressWarnings("UnusedParameters") View view) {
        new AddStockDialog().show(getFragmentManager(), "StockDialogFragment");
    }

    void addStock(final String symbol) {
        if (symbol != null && !symbol.isEmpty()) {

            if (BasicUtils.isNetworkUp(this)) {
                swipeRefreshLayout.setRefreshing(true);
            } else {
                String message = getString(R.string.toast_stock_added_no_connectivity, symbol);
                snackbar = Snackbar.make(activityMainLayout,
                        message,
                        Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(getString(R.string.try_again), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
                snackbar.setActionTextColor(getResources().getColor(R.color.material_red_700));
                snackbar.show();
                hideMessage();
            }

            // check if symbol exists already
            if(!PrefUtils.getStocks(this).contains(symbol))
                PrefUtils.addStock(this, symbol);
            else {
                String message = getString(R.string.toast_symbol_exists, symbol);
                        snackbar = Snackbar.make(activityMainLayout,
                        message,
                        Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(getString(R.string.ok), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRefresh();
                    }
                });
                snackbar.setActionTextColor(getResources().getColor(R.color.material_red_700));
                snackbar.show();
            }

            QuoteSyncJob.syncImmediately(this);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this,
                Contract.Quote.URI,
                Contract.Quote.QUOTE_COLUMNS,
                null, null, Contract.Quote.COLUMN_SYMBOL);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.setCursor(data);
        updateEmptyView();
        if (data.getCount() != 0) {
            supportStartPostponedEnterTransition();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        swipeRefreshLayout.setRefreshing(false);
        adapter.setCursor(null);
    }

    private void setDisplayModeMenuItemIcon(MenuItem item) {
        if (PrefUtils.getDisplayMode(this)
                .equals(getString(R.string.pref_display_mode_absolute_key))) {
            item.setIcon(R.drawable.ic_percentage);
        } else {
            item.setIcon(R.drawable.ic_dollar);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_settings, menu);
        MenuItem item = menu.findItem(R.id.action_change_units);
        setDisplayModeMenuItemIcon(item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_change_units) {
            PrefUtils.toggleDisplayMode(this);
            setDisplayModeMenuItemIcon(item);
            adapter.notifyDataSetChanged();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_stock_status_key))) {
            updateEmptyView();
        }
    }

    @SuppressLint("SwitchIntDef")
    private void updateEmptyView() {

        if (!BasicUtils.isNetworkUp(this)) {
            showErrorMessage(R.string.error_no_network);
            return;
        }

        if (PrefUtils.getStocks(this).size() == 0) {
            showErrorMessage(R.string.error_no_stocks);
            return;
        }

        @QuoteSyncJob.StockStatus int status = PrefUtils.getStockStatus(this);

        // Show loading msg if sync is running, whether first time or subsequent sync
        if (status == QuoteSyncJob.STOCK_STATUS_LOADING) {
            showMessage(R.string.loading_data);
            return;
        }

        // Hide loading msg if sync stopped due to any error, whether first time or subsequent sync
        switch (status) {
            case QuoteSyncJob.STOCK_STATUS_EMPTY:
                showErrorMessage(R.string.error_no_stocks);
                break;
            case QuoteSyncJob.STOCK_STATUS_SERVER_DOWN:
                showErrorMessage(R.string.error_server_down);
                break;
            case QuoteSyncJob.STOCK_STATUS_SERVER_INVALID:
                showErrorMessage(R.string.error_server_invalid);
                break;
            case QuoteSyncJob.STOCK_STATUS_UNKNOWN:
                showErrorMessage(R.string.empty_stock_list);
                break;
        }

        // Hide loading msf if sync completed
        if (status == QuoteSyncJob.STOCK_STATUS_OK)
            hideMessage();
    }

    private void showErrorMessage(int message) {
        BasicUtils.showToast(this, getString(message));
        hideMessage();
        return;
    }

    private void showMessage(int message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);
    }

    private void hideMessage() {
        error.setText("");
        error.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
    }

    private void showInternetOffSnackBar() {
        snackbar = Snackbar.make(activityMainLayout,
                getString(R.string.error_no_network),
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(getString(R.string.try_again), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRefresh();
                if (!BasicUtils.isNetworkUp(MainActivity.this)) {
                    showInternetOffSnackBar();
                }
            }
        });
        snackbar.setActionTextColor(getResources().getColor(R.color.material_red_700));
        snackbar.show();
    }

    @Override
    protected void onDestroy() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }
}
