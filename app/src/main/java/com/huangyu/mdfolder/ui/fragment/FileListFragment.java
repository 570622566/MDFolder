package com.huangyu.mdfolder.ui.fragment;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.huangyu.library.app.BaseApplication;
import com.huangyu.library.ui.BaseFragment;
import com.huangyu.library.ui.CommonRecyclerViewAdapter;
import com.huangyu.library.util.FileUtils;
import com.huangyu.library.util.LogUtils;
import com.huangyu.mdfolder.R;
import com.huangyu.mdfolder.app.Constants;
import com.huangyu.mdfolder.bean.FileItem;
import com.huangyu.mdfolder.mvp.presenter.FileListPresenter;
import com.huangyu.mdfolder.mvp.view.IFileListView;
import com.huangyu.mdfolder.ui.activity.AudioBrowserActivity;
import com.huangyu.mdfolder.ui.activity.FileListActivity;
import com.huangyu.mdfolder.ui.activity.ImageBrowserActivity;
import com.huangyu.mdfolder.ui.activity.VideoBrowserActivity;
import com.huangyu.mdfolder.ui.adapter.FileListAdapter;
import com.huangyu.mdfolder.ui.widget.TabView;
import com.huangyu.mdfolder.ui.widget.delegate.WindowCallbackDelegate;
import com.huangyu.mdfolder.utils.AlertUtils;
import com.huangyu.mdfolder.utils.CompressUtils;
import com.huangyu.mdfolder.utils.KeyboardUtils;
import com.huangyu.mdfolder.utils.SPUtils;
import com.jakewharton.rxbinding.view.RxView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.functions.Action1;

/**
 * Created by huangyu on 2017-5-23.
 */
public class FileListFragment extends BaseFragment<IFileListView, FileListPresenter> implements IFileListView {

    @BindView(R.id.cl_main)
    CoordinatorLayout mCoordinatorLayout;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;

    @BindView(R.id.tab_view)
    TabView mTabView;

    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.ll_empty)
    LinearLayout mLlEmpty;

    @BindView(R.id.iv_center)
    ImageView mIvCenter;

    @BindView(R.id.fam_add)
    FloatingActionMenu mFamAdd;

    @BindView(R.id.fab_add_file)
    FloatingActionButton mFabAddFile;

    @BindView(R.id.fab_add_folder)
    FloatingActionButton mFabAddFolder;

    private ProgressDialog mProgressDialog;
    private FileListAdapter mAdapter;
    private ActionMode mActionMode;
    private String mSearchStr = "";

    private WindowCallbackDelegate mWindowCallbackDelegate;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_main;
    }

    @Override
    protected IFileListView initAttachView() {
        return this;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mIvCenter.setColorFilter(getResources().getColor(R.color.colorDarkGrey));

        mAdapter = new FileListAdapter(getContext());
        mAdapter.setOnItemClick(new CommonRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (mPresenter.mEditType == Constants.EditType.NONE) {
                    FileItem file = mAdapter.getItem(position);
                    if (file == null) {
                        finishAction();
                        mPresenter.refreshAfterFinishAction();
                        return;
                    }

                    if (file.isDirectory()) {
                        mPresenter.enterFolder(file, getScrollYDistance());
                    } else {
                        if (SPUtils.isBuildInMode()) {
                            // 压缩包文件
                            if (file.getType() == Constants.FileType.COMPRESS) {
                                if (FileUtils.getSuffix(file.getName()).equals(".zip")) {
                                    AlertUtils.showCompressListBottomSheet(getContext(), CompressUtils.listZipFiles(file.getPath()), ".zip");
                                } else if (FileUtils.getSuffix(file.getName()).equals(".rar")) {
                                    AlertUtils.showCompressListBottomSheet(getContext(), CompressUtils.listRarFiles(file.getPath()), ".rar");
                                } else {
                                    if (!mPresenter.openFile(getContext(), new File(file.getPath()))) {
                                        showMessage(getString(R.string.tips_can_not_access_file));
                                    }
                                }
                                return;
                            }
                            // 同目录下图片浏览
                            if (file.getType() == Constants.FileType.SINGLE_IMAGE) {
                                int currentPosition = 0;
                                ArrayList<FileItem> dataList = mAdapter.getDataList();
                                ArrayList<FileItem> arrayList = new ArrayList<>();
                                for (int i = 0; i < dataList.size(); i++) {
                                    if (dataList.get(i).getType() == Constants.FileType.SINGLE_IMAGE) {
                                        arrayList.add(dataList.get(i));
                                    }
                                }
                                for (int i = 0; i < arrayList.size(); i++) {
                                    if (arrayList.get(i).getPath().equals(file.getPath())) {
                                        currentPosition = i;
                                    }
                                }
                                Intent intent = new Intent(getActivity(), ImageBrowserActivity.class);
                                intent.putExtra(getString(R.string.intent_image_list), arrayList);
                                intent.putExtra(getString(R.string.intent_image_position), currentPosition);
                                intent.putExtra(getString(R.string.intent_image_sort_type), mPresenter.mSortType);
                                intent.putExtra(getString(R.string.intent_image_order_type), mPresenter.mOrderType);
                                getActivity().startActivity(intent);
                            }
                            // 同目录视频浏览
                            else if (file.getType() == Constants.FileType.SINGLE_VIDEO) {
                                int currentPosition = 0;
                                ArrayList<FileItem> arrayList = new ArrayList<>();
                                ArrayList<FileItem> dataList = mAdapter.getDataList();
                                for (int i = 0; i < dataList.size(); i++) {
                                    if (dataList.get(i).getType() == Constants.FileType.SINGLE_VIDEO) {
                                        arrayList.add(dataList.get(i));
                                    }
                                }
                                for (int i = 0; i < arrayList.size(); i++) {
                                    if (arrayList.get(i).getPath().equals(file.getPath())) {
                                        currentPosition = i;
                                    }
                                }
                                Intent intent = new Intent(getActivity(), VideoBrowserActivity.class);
                                intent.putExtra(getString(R.string.intent_video_list), arrayList);
                                intent.putExtra(getString(R.string.intent_video_position), currentPosition);
                                getActivity().startActivity(intent);
                            }
                            // 同目录音频浏览
                            else if (file.getType() == Constants.FileType.SINGLE_AUDIO) {
                                int currentPosition = 0;
                                ArrayList<FileItem> arrayList = new ArrayList<>();
                                ArrayList<FileItem> dataList = mAdapter.getDataList();
                                for (int i = 0; i < dataList.size(); i++) {
                                    if (dataList.get(i).getType() == Constants.FileType.SINGLE_AUDIO) {
                                        arrayList.add(dataList.get(i));
                                    }
                                }
                                for (int i = 0; i < arrayList.size(); i++) {
                                    if (arrayList.get(i).getPath().equals(file.getPath())) {
                                        currentPosition = i;
                                    }
                                }
                                Intent intent = new Intent(getActivity(), AudioBrowserActivity.class);
                                intent.putExtra(getString(R.string.intent_audio_list), arrayList);
                                intent.putExtra(getString(R.string.intent_audio_position), currentPosition);
                                getActivity().startActivity(intent);
                            }
                            // 进入图片浏览
                            else if (file.getType() == Constants.FileType.IMAGE) {
                                Intent intent = new Intent(getActivity(), ImageBrowserActivity.class);
                                intent.putExtra(getString(R.string.intent_image_list), mAdapter.getDataList());
                                intent.putExtra(getString(R.string.intent_image_position), position);
                                intent.putExtra(getString(R.string.intent_image_sort_type), mPresenter.mSortType);
                                intent.putExtra(getString(R.string.intent_image_order_type), mPresenter.mOrderType);
                                getActivity().startActivity(intent);
                            }
                            // 进入视频浏览
                            else if (file.getType() == Constants.FileType.VIDEO) {
                                Intent intent = new Intent(getActivity(), VideoBrowserActivity.class);
                                intent.putExtra(getString(R.string.intent_video_list), mAdapter.getDataList());
                                intent.putExtra(getString(R.string.intent_video_position), position);
                                getActivity().startActivity(intent);
                            }
                            // 进入音频浏览
                            else if (file.getType() == Constants.FileType.AUDIO) {
                                AudioBrowserActivity.mFileList = mAdapter.getDataList();
                                Intent intent = new Intent(getActivity(), AudioBrowserActivity.class);
                                // bytes[] make TransactionTooLargeException
                                // intent.putExtra(getString(R.string.intent_audio_list), mAdapter.getDataList());
                                intent.putExtra(getString(R.string.intent_audio_position), position);
                                getActivity().startActivity(intent);
                            }
                            // 安装包直接打开应用
                            else if (file.getType() == Constants.FileType.INSTALLED) {
                                PackageManager pack = getContext().getPackageManager();
                                Intent app = pack.getLaunchIntentForPackage(file.getPackageName());
                                if (app == null) {
                                    showMessage(getString(R.string.tips_can_not_access_file));
                                    return;
                                }
                                startActivity(app);
                            }
                            // 打开文件
                            else if (!mPresenter.openFile(getContext(), new File(file.getPath()))) {
                                showMessage(getString(R.string.tips_can_not_access_file));
                            }
                        } else {
                            if (!mPresenter.openFile(getContext(), new File(file.getPath()))) {
                                showMessage(getString(R.string.tips_can_not_access_file));
                            }
                        }
                    }

                    finishAction();
                    mPresenter.refreshAfterFinishAction();
                } else if (mPresenter.mEditType == Constants.EditType.COPY || mPresenter.mEditType == Constants.EditType.MOVE
                        || mPresenter.mEditType == Constants.EditType.ZIP || mPresenter.mEditType == Constants.EditType.UNZIP) {
                    FileItem file = mAdapter.getItem(position);
                    if (file == null) {
                        return;
                    }

                    if (file.isDirectory()) {
                        mPresenter.enterFolder(file, getScrollYDistance());
                    }
                } else {
                    mPresenter.mEditType = Constants.EditType.SELECT;
                    mAdapter.switchSelectedState(position);
                    mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                    if (mAdapter.getSelectedItemCount() == 0) {
                        finishAction();
                        mPresenter.refreshAfterFinishAction();
                    }
                }
            }
        });

        mAdapter.setOnItemLongClick(new CommonRecyclerViewAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(View view, final int position) {
                if (mPresenter.mEditType == Constants.EditType.COPY || mPresenter.mEditType == Constants.EditType.MOVE
                        || mPresenter.mEditType == Constants.EditType.ZIP || mPresenter.mEditType == Constants.EditType.UNZIP) {
                    return;
                }
                mPresenter.mEditType = Constants.EditType.SELECT;
                mAdapter.switchSelectedState(position);
                if (mActionMode == null) {
                    mActionMode = getControlActionMode();
                }
                mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
            }
        });

        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        String themeMode = ((FileListActivity) getActivity()).getThemeMode();
        switch (themeMode) {
            case "1":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryDark));
                break;
            case "2":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryIndigo));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryIndigo));
                break;
            case "3":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryCyan));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryCyan));
                break;
            case "4":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryTeal));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryTeal));
                break;
            case "5":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryGreen));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryGreen));
                break;
            case "6":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryRed));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryRed));
                break;
            case "7":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryPurple));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryPurple));
                break;
            case "8":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryOrange));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryOrange));
                break;
            case "9":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryYellow));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryYellow));
                break;
            case "10":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryPink));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryPink));
                break;
            case "11":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryBrown));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryBrown));
                break;
            case "12":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryGrey));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryGrey));
                break;
            case "13":
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryBlack));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryBlack));
                break;
            default:
                mTabView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryBlue));
                mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimaryBlue));
                break;
        }

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mPresenter.onRefreshInSwipe(mSearchStr, false);
            }
        });

        RxView.clicks(mFabAddFile).throttleFirst(1, TimeUnit.SECONDS).subscribe(new Action1<Void>() {
            @Override
            public void call(Void aVoid) {
                mPresenter.onAddFile();
            }
        });

        RxView.clicks(mFabAddFolder).throttleFirst(1, TimeUnit.SECONDS).subscribe(new Action1<Void>() {
            @Override
            public void call(Void aVoid) {
                mPresenter.onAddFolder();
            }
        });

        initRxManagerActions();

//        mPresenter.onLoadStorageFileList(false, mSearchStr);
    }

    private void initRxManagerActions() {
        mRxManager.on("onSearch", new Action1<String>() {
            @Override
            public void call(String text) {
                // 内置存储全局搜索
                if (SPUtils.isSearchGlobally()) {
                    if (TextUtils.isEmpty(text)) {
                        mPresenter.onRefreshInSwipe(mSearchStr, false);
                    } else {
                        mPresenter.onSearchFileList(text);
                    }
                }
                // 单个文件迭代搜索
                else {
                    mSearchStr = text;
                    mPresenter.onRefreshInSwipe(mSearchStr, false);
                }
            }
        });

        mRxManager.on("toStorage", new Action1<Boolean>() {
            @Override
            public void call(Boolean isOuter) {
                if (isOuter) {
                    mPresenter.mSelectType = Constants.SelectType.MENU_SDCARD;
                } else {
                    mPresenter.mSelectType = Constants.SelectType.MENU_FILE;
                }
                mPresenter.onLoadStorageFileList(isOuter, mSearchStr);
            }
        });

        mRxManager.on("toRoot", new Action1<String>() {
            @Override
            public void call(String text) {
                mPresenter.mSelectType = Constants.SelectType.MENU_FILE;
                mPresenter.onLoadRootFileList(mSearchStr);
            }
        });

        mRxManager.on("toRecent", new Action1<String>() {
            @Override
            public void call(String text) {
                mPresenter.mSelectType = Constants.SelectType.MENU_RECENT;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toPhoto", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_PHOTO;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toMusic", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_MUSIC;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toVideo", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_VIDEO;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toDocument", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_DOCUMENT;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toDownload", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_DOWNLOAD;
                mPresenter.onLoadDownloadFileList(mSearchStr);
            }
        });

        mRxManager.on("toApk", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_APK;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toZip", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_ZIP;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toApps", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_APPS;
                mPresenter.onLoadMultiTypeFileList(mSearchStr);
            }
        });

        mRxManager.on("toPath", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.mSelectType = Constants.SelectType.MENU_FILE;
                mPresenter.onLoadBookmarkFileList(mSearchStr, s);
            }
        });

        mRxManager.on("onSortType", new Action1<Integer>() {
            @Override
            public void call(Integer sortType) {
                mPresenter.mSortType = sortType;
                mPresenter.onRefresh(mSearchStr, true, 0);
            }
        });

        mRxManager.on("onOrderType", new Action1<Integer>() {
            @Override
            public void call(Integer orderType) {
                mPresenter.mOrderType = orderType;
                mPresenter.onRefresh(mSearchStr, true, 0);
            }
        });

        mRxManager.on("onDeleteAndRefresh", new Action1<String>() {
            @Override
            public void call(String orderType) {
                mPresenter.onLoadStorageFileList(false, mSearchStr);
            }
        });

        mRxManager.on("onUninstall", new Action1<Integer>() {

            @Override
            public void call(Integer position) {
                finishAction();
                mAdapter.removeItem(position);
            }
        });

        mRxManager.on("onSaveBookmark", new Action1<String>() {
            @Override
            public void call(String s) {
                mPresenter.onSaveBookmark();
            }
        });

    }

    @Override
    public void refreshBookmarkList() {
        ((FileListActivity) getActivity()).refreshBookMarkList();
    }

    @Override
    public void startRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);
    }

    @Override
    public void stopRefresh() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void addTab(String path) {
        mTabView.addTab(path, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = v.getTag(R.id.tab_tag);
                if (tag != null && tag instanceof Integer) {
                    int index = (Integer) tag;
                    mPresenter.enterCertainFolder(index);
                }
                if (mPresenter.mEditType != Constants.EditType.COPY && mPresenter.mEditType != Constants.EditType.MOVE
                        && mPresenter.mEditType != Constants.EditType.ZIP && mPresenter.mEditType != Constants.EditType.UNZIP) {
                    finishAction();
                    mPresenter.refreshAfterFinishAction();
                }
            }
        });
    }

    @Override
    public boolean removeTab() {
        return mTabView.removeTab();
    }

    @Override
    public void removeAllTabs() {
        mTabView.removeAllTabs();
    }

    @Override
    public void hideTabs() {
        mTabView.hideTabs();
    }

    @Override
    public void showTabs() {
        if (mTabView.isHide()) {
            mTabView.showTabs();
        }
    }

    @Override
    public void addData(FileItem fileItem) {
        mAdapter.addItem(fileItem);
        mAdapter.clearSelectedState();
    }

    @Override
    public void changeData(FileItem fileItem, int position) {
        mAdapter.changeItem(fileItem, position);
    }

    @Override
    public void deleteData(int position) {
        mAdapter.removeItem(position);
    }

    @Override
    public void clearSelectedState() {
        mAdapter.clearSelectedState();
    }

    @Override
    public void refreshData(boolean ifClearSelected) {
        mPresenter.onRefreshInSwipe(mSearchStr, ifClearSelected);
    }

    @Override
    public void refreshData(boolean ifClearSelected, final int scrollY) {
        mPresenter.onRefresh(mSearchStr, ifClearSelected, scrollY);
    }

    @Override
    public void refreshView(ArrayList<FileItem> filesList, boolean ifClearSelected, final int scrollY) {
        mAdapter.clearData(ifClearSelected);

        if (filesList == null || filesList.isEmpty()) {
            mLlEmpty.setVisibility(View.VISIBLE);
            showFloatingActionMenu();
            return;
        } else {
            mLlEmpty.setVisibility(View.GONE);
            mAdapter.setData(filesList);
        }

        if (scrollY != 0) {
            mRecyclerView.scrollToPosition(0);
            mRecyclerView.scrollBy(0, scrollY);
        } else {
            mRecyclerView.scrollToPosition(0);
            mRecyclerView.scrollTo(0, scrollY);
        }

        showFloatingActionMenu();
    }

    @Override
    public void showMessage(String message) {
        AlertUtils.showToast(getContext(), message);
    }

    @Override
    public void showError(String error) {
        if (BaseApplication.isDebug()) {
            LogUtils.logd(error);
//            LogToFileUtils.saveCrashInfoFile(error);
        }
        AlertUtils.showToast(getContext(), getString(R.string.tips_error));
    }

    @Override
    public void showKeyboard(final EditText editText) {
        getActivity().getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                KeyboardUtils.showSoftInput(editText);
            }
        }, 200);
    }

    @Override
    public void hideKeyboard(final EditText editText) {
        getActivity().getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                KeyboardUtils.hideSoftInput(getContext(), editText);
            }
        }, 200);
    }

    @Override
    public void showInfoBottomSheet(FileItem fileItem, DialogInterface.OnCancelListener onCancelListener) {
        AlertUtils.showFileInfoBottomSheet(getContext(), fileItem, onCancelListener);
    }

    @Override
    public View inflateFilenameInputDialogLayout() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.dialog_input, new LinearLayout(getContext()), false);
    }

    @Override
    public View inflatePasswordInputDialogLayout() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.dialog_password, new LinearLayout(getContext()), false);
    }

    @Override
    public TextInputLayout findTextInputLayout(View view) {
        return (TextInputLayout) ButterKnife.findById(view, R.id.til_tips);
    }

    @Override
    public EditText findAlertDialogEditText(View view) {
        return (AppCompatEditText) ButterKnife.findById(view, R.id.et_name);
    }

    @Override
    public AlertDialog showInputFileNameAlert(View view, DialogInterface.OnShowListener onShowListener) {
        return AlertUtils.showCustomAlert(getContext(), "", view, onShowListener);
    }

    @Override
    public AlertDialog showNormalAlert(String message, String positiveString, DialogInterface.OnClickListener positiveClick) {
        return AlertUtils.showNormalAlert(getContext(), message, positiveString, positiveClick);
    }

    @Override
    public void closeFloatingActionMenu() {
        mFamAdd.close(true);
    }

    @Override
    public void showFloatingActionMenu() {
        mFamAdd.showMenuButton(true);
    }

    @Override
    public String getResString(@StringRes int resId) {
        return getContext().getString(resId);
    }

    public void finishAction() {
        mSearchStr = "";
        if (mActionMode != null) {
            mActionMode.finish();
        }
        mPresenter.mEditType = Constants.EditType.NONE;
    }

    @Override
    public void showProgressDialog(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog = new ProgressDialog(getContext());
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.setTitle(getString(R.string.tips_alert));
                mProgressDialog.setMessage(message);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
        });
    }

    @Override
    public void showProgressDialog(final int totalCount, final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog = new ProgressDialog(getContext());
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setMessage(message);
                mProgressDialog.setMax(totalCount);
                mProgressDialog.setProgress(0);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
        });
    }

    @Override
    public void updateProgressDialog(final int count) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    final int progress = mProgressDialog.getProgress() + count;

                    mProgressDialog.setProgress(progress);
                    if (progress == mProgressDialog.getMax()) {
                        hideProgressDialog();
                    }
                }
            }
        });
    }

    @Override
    public void hideProgressDialog() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
        });
    }

    private ActionMode getControlActionMode() {
        return getActivity().startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mPresenter.isActionModeActive = true;
                mPresenter.isActionModeActive = false;
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                if (mPresenter.mSelectType == Constants.SelectType.MENU_APPS) {
                    mode.getMenuInflater().inflate(R.menu.menu_control_apps, menu);
                } else if (mPresenter.mSelectType == Constants.SelectType.MENU_SDCARD) {
                    mode.getMenuInflater().inflate(R.menu.menu_control_sdcard, menu);
                } else {
                    mode.getMenuInflater().inflate(R.menu.menu_control, menu);
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                final ArrayList<FileItem> fileList = mAdapter.getSelectedDataList();
                final ArrayList<Integer> selectedItemList = mAdapter.getSelectedItemList();
                switch (item.getItemId()) {
                    case R.id.action_rename:
                        mPresenter.onRenameFile(fileList, selectedItemList);
                        break;
                    case R.id.action_info:
                        mPresenter.onShowFileInfo(fileList, selectedItemList);
                        break;
                    case R.id.action_share:
                        File file;
                        List<File> files = new ArrayList<>();
                        for (FileItem fileItem : fileList) {
                            file = new File(fileItem.getPath());
                            files.add(file);
                        }
                        mPresenter.shareFile(getContext(), files);
                        break;
                    case R.id.action_delete:
                        mPresenter.onDelete(fileList, selectedItemList);
                        break;
                    case R.id.action_copy:
                        mPresenter.mEditType = Constants.EditType.COPY;
                        mAdapter.mSelectedFileList = fileList;
                        mActionMode = getPasteActonMode();
                        mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        break;
                    case R.id.action_move:
                        mPresenter.mEditType = Constants.EditType.MOVE;
                        mAdapter.mSelectedFileList = fileList;
                        mActionMode = getPasteActonMode();
                        mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        break;
                    case R.id.action_show_hide:
                        mPresenter.onShowHideFile(fileList);
                        break;
                    case R.id.action_compress:
                        mPresenter.mEditType = Constants.EditType.ZIP;
                        mAdapter.mSelectedFileList = fileList;
                        mActionMode = getPasteActonMode();
                        mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        break;
                    case R.id.action_extract:
                        if (fileList.size() != 1) {
                            showMessage(getResString(R.string.tips_choose_one_file));
                        } else {
                            mAdapter.mSelectedFileList = fileList;
                            mPresenter.mEditType = Constants.EditType.UNZIP;
                            mActionMode = getPasteActonMode();
                            mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        }
                        break;
                    case R.id.action_select_all:
                        mPresenter.mEditType = Constants.EditType.SELECT;
                        mAdapter.selectAll();
                        mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        if (mAdapter.getSelectedItemCount() == 0) {
                            finishAction();
                            mPresenter.refreshAfterFinishAction();
                        }
                        break;
                    case R.id.action_inverse_all:
                        mPresenter.mEditType = Constants.EditType.SELECT;
                        mAdapter.inverseAll();
                        mActionMode.setTitle(String.format(getString(R.string.tips_selected), mAdapter.getSelectedItemCount()));
                        if (mAdapter.getSelectedItemCount() == 0) {
                            finishAction();
                            mPresenter.refreshAfterFinishAction();
                        }
                        break;
                    case R.id.action_uninstall:
                        mPresenter.onUninstall(fileList, selectedItemList);
                        break;
                    case R.id.action_remark:
                        mPresenter.onRemark(fileList, selectedItemList);
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                if (mPresenter.mEditType != Constants.EditType.COPY && mPresenter.mEditType != Constants.EditType.MOVE
                        && mPresenter.mEditType != Constants.EditType.ZIP && mPresenter.mEditType != Constants.EditType.UNZIP) {
                    getActivity().supportInvalidateOptionsMenu();
                    mActionMode = null;
                    mPresenter.mEditType = Constants.EditType.NONE;
                    mAdapter.clearSelectedState();
                }
                mPresenter.isActionModeActive = false;
            }
        });
    }

    private ActionMode getPasteActonMode() {
        return getActivity().startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mPresenter.isActionModeActive = true;
                mPresenter.isPasteActonMode = true;
                mWindowCallbackDelegate = new WindowCallbackDelegate(getActivity().getWindow().getCallback(), getActivity());
                getActivity().getWindow().setCallback(mWindowCallbackDelegate);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                mode.getMenuInflater().inflate(R.menu.menu_paste, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                final ArrayList<FileItem> fileList = mAdapter.mSelectedFileList;
                switch (item.getItemId()) {
                    case R.id.action_paste:
                        if (mPresenter.mEditType == Constants.EditType.COPY) {
                            mPresenter.onCopy(fileList);
                        } else if (mPresenter.mEditType == Constants.EditType.MOVE) {
                            mPresenter.onMove(fileList);
                        } else if (mPresenter.mEditType == Constants.EditType.ZIP) {
                            mPresenter.onCompress(fileList);
                        } else if (mPresenter.mEditType == Constants.EditType.UNZIP) {
                            mPresenter.onExtract(fileList);
                        }
                        break;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                getActivity().supportInvalidateOptionsMenu();
                mActionMode = null;
                mAdapter.mSelectedFileList = null;
                mPresenter.mEditType = Constants.EditType.NONE;
                mPresenter.isPasteActonMode = false;
                mPresenter.isActionModeActive = false;
                mAdapter.clearSelectedState();
                Window.Callback originalWindowCallback = mWindowCallbackDelegate.getOriginalWindowCallback();
                if (originalWindowCallback != null) {
                    getActivity().getWindow().setCallback(originalWindowCallback);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        finishAction();
        super.onDestroy();
    }

    public boolean onBackPressed() {
        if (mFamAdd.isOpened()) {
            mFamAdd.close(true);
            return true;
        }
        return mPresenter.backFolder();
    }

    @Override
    public void onDestroyView() {
        hideProgressDialog();
        super.onDestroyView();
    }

    public int getScrollYDistance() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
        int position = layoutManager.findFirstVisibleItemPosition();
        View firstVisibleChildView = layoutManager.findViewByPosition(position);
        int itemHeight = firstVisibleChildView.getHeight();
        return (position) * itemHeight - firstVisibleChildView.getTop();
    }

    public boolean isActionModeActive() {
        return mPresenter.isActionModeActive;
    }

}
