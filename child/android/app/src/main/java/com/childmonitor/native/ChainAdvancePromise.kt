package com.childmonitor.native

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap

/** Promise sink that only signals completion — used for sequential runtime permission chains. */
class ChainAdvancePromise(private val onComplete: () -> Unit) : Promise {
    override fun resolve(value: Any?) = onComplete()
    override fun reject(code: String, message: String?) = onComplete()
    override fun reject(code: String, userInfo: WritableMap) = onComplete()
    override fun reject(code: String, message: String?, userInfo: WritableMap) = onComplete()
    override fun reject(code: String, message: String?, throwable: Throwable?) = onComplete()
    override fun reject(code: String, throwable: Throwable?) = onComplete()
    override fun reject(code: String, throwable: Throwable?, userInfo: WritableMap) = onComplete()
    override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) = onComplete()
    override fun reject(throwable: Throwable, userInfo: WritableMap) = onComplete()
    override fun reject(message: String) = onComplete()
    override fun reject(throwable: Throwable) = onComplete()
}
