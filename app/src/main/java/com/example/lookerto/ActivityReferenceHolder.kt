package com.example.lookerto

import java.lang.ref.WeakReference

object ActivityReferenceHolder {  private var activityRef: WeakReference<floting>? = null

    fun setActivity(activity: floting) {
        activityRef = WeakReference(activity)
    }

    fun getActivity(): floting? {
        return activityRef?.get()
    }

    fun clear() {
        activityRef = null
    }
}