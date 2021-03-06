package com.sum.library.framework;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.sum.library.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Sum on 15/11/26.
 */
public class FragmentCacheManager {

    public static FragmentCacheManager instance() {
        return new FragmentCacheManager();
    }

    private FragmentCacheManager() {
        mCacheFragment = new HashMap<>();
    }

    //Activity 中的Fragment管理
    private FragmentManager mFragmentManager;

    private Activity mActivity;

    private Fragment mFragment;

    private int mContainerId;

    private long mLastBackTime;

    private BootCallBackListener mListener;

    //缓存的Fragment集合数据
    private HashMap<Object, FragmentInfo> mCacheFragment;

    public static boolean DEBUG = false;

    //当前展示的Fragment
    private Fragment mCurrentFragment;
    private Object mCurrentFragmentIndex = null;

    public void setUp(FragmentActivity activity, @IdRes int containerId) {
        if (mFragment != null) {
            throw new RuntimeException("you have setup for Fragment");
        }
        this.mActivity = activity;
        this.mContainerId = containerId;
        mFragmentManager = activity.getSupportFragmentManager();
    }

    public void setUp(Fragment fragment, @IdRes int containerId) {
        if (mActivity != null) {
            throw new RuntimeException("you have setup for Activity");
        }
        this.mFragment = fragment;
        this.mContainerId = containerId;
        mFragmentManager = fragment.getChildFragmentManager();
        //Fragment所在的Activity
        mActivity = fragment.getActivity();
    }

    /**
     * 获取所有显示过的缓存的Fragment
     *
     * @return
     */
    public List<Fragment> getAllCacheFragment() {
        ArrayList<Fragment> list = new ArrayList<>();
        Set<Map.Entry<Object, FragmentInfo>> entries = mCacheFragment.entrySet();
        for (Map.Entry<Object, FragmentInfo> entry : entries) {
            FragmentInfo info = entry.getValue();
            if (info.fragment != null) {
                list.add(info.fragment);
            }
        }
        return list;
    }

    /**
     * 获取缓存的Fragment
     *
     * @param index
     * @return
     */
    public Fragment getCacheFragment(Object index) {

        return mCacheFragment.get(index).fragment;
    }

    /**
     * 显示index对应的Fragment
     *
     * @param index
     */
    public void setCurrentFragment(Object index) {
        if (index == mCurrentFragmentIndex) {
            return;
        }
        FragmentInfo info = mCacheFragment.get(index);
        mCurrentFragmentIndex = index;
        goToThisFragment(info);
    }

    /**
     * 添加Fragment到管理栈里，同一个实力对象只会创建一次
     * 功能实现原理FragmentTabhost相同,注意hide和detach区别
     */

    public void addFragmentToCache(Object index, Class<?> clss) {
        FragmentInfo info = createInfo(clss.getName(), clss, null);
        mCacheFragment.put(index, info);
    }

    /**
     * 要实现同一个对象多次创建必须通过不同的Tag来做唯一标示
     *
     * @param index Fragment对应的索引，通过索引找到对应的显示Fragment
     * @param c     需要创建的Fragment.class 文件
     * @param tag   Fragment的唯一标示
     * @param args  Bundle 会传递给生产的Fragment对象，
     */
    public void addFragmentToCache(Object index, Class<?> c, String tag, Bundle args) {
        FragmentInfo info = createInfo(tag, c, args);
        mCacheFragment.put(index, info);
    }


    public void setListener(BootCallBackListener listener) {
        this.mListener = listener;
    }

    private static final class FragmentInfo {
        private final String tag;
        private final Class<?> clss;
        private final Bundle args;
        Fragment fragment;

        FragmentInfo(String _tag, Class<?> _class, Bundle _args) {
            tag = _tag;
            clss = _class;
            args = _args;
        }
    }

    private FragmentInfo createInfo(String tag, Class<?> clss, Bundle args) {
        return new FragmentInfo(tag, clss, args);
    }

    private void goToThisFragment(FragmentInfo param) {
        int containerId = mContainerId;
        Class<?> cls = param.clss;
        if (cls == null) {
            return;
        }
        try {
            if (DEBUG) {
                Logger.d("before operate, stack entry count: " + mFragmentManager.getBackStackEntryCount());
            }
            String fragmentTag = param.tag;
            //通过Tag查找活动的Fragment，相同到Fragment可以创建多个实力对象通过设置不同到Tag
            Fragment fragment = mFragmentManager.findFragmentByTag(fragmentTag);
            if (fragment == null) {
                //创建对象将数据传递给Fragment对象
                fragment = (Fragment) cls.newInstance();
                if (param.args != null) {
                    fragment.setArguments(param.args);
                }
                param.fragment = fragment;
                if (DEBUG) {
                    Logger.d("newInstance " + fragmentTag);
                }
            }
            if (mCurrentFragment != null && mCurrentFragment != fragment) {
                //去除跟Activity关联的Fragment
                //detach会将Fragment所占用的View从父容器中移除，但不会完全销毁，还处于活动状态
                mFragmentManager.beginTransaction().detach(mCurrentFragment).commit();
                if (DEBUG) {
                    Logger.d("detach " + mCurrentFragment.getClass().getName());
                }
            }

            FragmentTransaction ft = mFragmentManager.beginTransaction();

            if (fragment.isDetached()) {
                //重新关联到Activity
                ft.attach(fragment);
                if (DEBUG) {
                    Logger.d("attach " + fragmentTag);
                }
            } else {
                if (DEBUG) {
                    Logger.d(fragmentTag + " is added");
                }
                if (!fragment.isAdded()) {
                    ft.add(containerId, fragment, fragmentTag);
                }
            }
            mCurrentFragment = fragment;

            ft.commitAllowingStateLoss();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    public void onBackPress() {

        if (mActivity.isTaskRoot()) {
            int cnt = mFragmentManager.getBackStackEntryCount();
            long secondClickBackTime = System.currentTimeMillis();
            if (cnt <= 1 && (secondClickBackTime - mLastBackTime) > 2000) {
                if (mListener != null) {
                    mListener.rootCallBack();
                }
                mLastBackTime = secondClickBackTime;
            } else {
                doReturnBack();
            }
        } else {
            doReturnBack();
        }
    }

    private void doReturnBack() {
        int count = mFragmentManager.getBackStackEntryCount();
        if (count <= 1) {
            mActivity.finish();
        } else {
            mFragmentManager.popBackStackImmediate();
        }
    }

    public interface BootCallBackListener {
        //返回键事件触发
        void rootCallBack();
    }
}
