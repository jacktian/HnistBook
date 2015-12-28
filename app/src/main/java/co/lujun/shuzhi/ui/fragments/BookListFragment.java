package co.lujun.shuzhi.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import co.lujun.shuzhi.App;
import co.lujun.shuzhi.R;
import co.lujun.shuzhi.api.Api;
import co.lujun.shuzhi.bean.Book;
import co.lujun.shuzhi.bean.Config;
import co.lujun.shuzhi.bean.JSONRequest;
import co.lujun.shuzhi.bean.ListData;
import co.lujun.shuzhi.ui.BookDetailActivity;
import co.lujun.shuzhi.ui.adapter.BookAdapter;
import co.lujun.shuzhi.util.IntentUtils;
import co.lujun.shuzhi.util.NetWorkUtils;
import co.lujun.shuzhi.util.SystemUtil;

/**
 * Created by lujun on 2015/3/17.
 */
public class BookListFragment extends BaseFragment {

    private View mView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecycleView;
    private LinearLayoutManager mLayoutManager;
    private BookAdapter mAdapter;
    private List<Book> mBooks;
    private Intent mBookDetailIntent;
    private Bundle mBundle;
    private String keyword = "";
    private int start = 0;
    private boolean hasMore = true;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    @Nullable @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_booklist, null);
        initView();
        return mView;
    }

    private void init(){
        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mBooks = new ArrayList<Book>();
        mAdapter = new BookAdapter(mBooks);
        mBundle = new Bundle();
        mBookDetailIntent = new Intent(getActivity(), BookDetailActivity.class);
    }

    private void initView(){
        if (mView != null){
            mSwipeRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.srl_booklist);
            mRecycleView = (RecyclerView) mView.findViewById(R.id.rv_booklist);
            mRecycleView.setLayoutManager(mLayoutManager);
            mRecycleView.setHasFixedSize(true);// 若每个item的高度固定，设置此项可以提高性能
            mRecycleView.setItemAnimator(new DefaultItemAnimator());// item 动画效果
            mAdapter.setOnItemClickListener(new BookAdapter.ViewHolder.ItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    mBundle.clear();
                    mBundle.putString(Config.BOOK.title.toString(),
                            ((Book) view.getTag()).getTitle());
                    mBundle.putString(Config.BOOK.isbn13.toString(),
                            (((Book) view.getTag()).getIsbn13()));
                    mBundle.putString(Config.BOOK.isbn10.toString(),
                            (((Book) view.getTag()).getIsbn10()));
                    mBookDetailIntent.putExtras(mBundle);
                    IntentUtils.startPreviewActivity(getActivity(), mBookDetailIntent);
                }
            });
            mRecycleView.setAdapter(mAdapter);
            mRecycleView.setOnScrollListener(
                    new RecyclerView.OnScrollListener() {
                        @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                int lastVisibleItem = mLayoutManager.findLastCompletelyVisibleItemPosition();
                                int totalItemCount = mLayoutManager.getItemCount();

                                if (lastVisibleItem == totalItemCount - 1 && hasMore) {
                                    onLoadMore();
                                }
                            }
                        }

                        @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);
                        }
                    }
            );
            mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    hasMore = true;
                    onUpdate(keyword);
                }
            });
            Bundle bundle;
            if (getActivity().getIntent() == null
                    || (bundle = getActivity().getIntent().getExtras()) == null){
                SystemUtil.showToast(R.string.msg_param_null);
                return;
            }
            keyword = bundle.getString(Config.BOOK_LST_SEARCH_KEY);
            if (TextUtils.isEmpty(keyword)){
                SystemUtil.showToast(R.string.msg_key_word_null);
                return;
            }
            try{
                keyword = URLEncoder.encode(keyword, "UTF-8");// 若关键字是中文，编码
                mSwipeRefreshLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                        onUpdate(keyword);
                    }
                });
            }catch (UnsupportedEncodingException e){
                e.printStackTrace();
            }
        }
    }

    private void onUpdate(String keyword){
        if (TextUtils.isEmpty(keyword)){
            SystemUtil.showToast(R.string.msg_key_word_null);
            mSwipeRefreshLayout.setRefreshing(false);
            return;
        }
        if (NetWorkUtils.getNetWorkType(getActivity()) == NetWorkUtils.NETWORK_TYPE_DISCONNECT){
            SystemUtil.showToast(R.string.msg_no_internet);
            mSwipeRefreshLayout.setRefreshing(false);
            return;
        }
        if (mSwipeRefreshLayout.isRefreshing()) {//检查是否正在刷新
            JSONRequest<ListData> jsonRequest = new JSONRequest<ListData>(
                    Api.BOOK_SEARCH_URL + "?q=" + keyword + "&start=0" + "&" + Api.API_KEY,
                    ListData.class,
                    new Response.Listener<ListData>() {
                        @Override
                        public void onResponse(ListData listData) {
                            setData(listData, true);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError volleyError) {
                            mSwipeRefreshLayout.setRefreshing(false);
                            SystemUtil.showToast(R.string.msg_find_error);
                        }
                    }
            );
            App.getRequestQueue().add(jsonRequest);
        }
    }

    private void onLoadMore(){
        JSONRequest<ListData> jsonRequest = new JSONRequest<ListData>(
                Api.BOOK_SEARCH_URL + "?q=" + keyword + "&start=" + start + "&" + Api.API_KEY,
                ListData.class,
                new Response.Listener<ListData>() {
                    @Override
                    public void onResponse(ListData listData) {
                        setData(listData, false);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        SystemUtil.showToast(R.string.msg_find_error);
                    }
                }
        );
        App.getRequestQueue().add(jsonRequest);
    }

    private void setData(ListData listData, boolean isUpdate){
        mSwipeRefreshLayout.setRefreshing(false);
        if (listData == null || listData.getBooks() == null) {
            SystemUtil.showToast(R.string.msg_no_find);
            return;
        }
        if (isUpdate) {// update
            mBooks.clear();
        }
        start += listData.getCount();

        if (listData.getBooks().size() <= 0){
            if (!isUpdate){
                hasMore = false;
            }
            SystemUtil.showToast(R.string.msg_no_find);
            return;
        }
        mBooks.addAll(listData.getBooks());
        mAdapter.notifyDataSetChanged();
    }
}
