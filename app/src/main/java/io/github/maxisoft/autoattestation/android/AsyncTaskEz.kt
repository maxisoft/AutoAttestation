package io.github.maxisoft.autoattestation.android

import android.os.AsyncTask

open class AsyncTaskEz<Params, Progress, Result>(val background: (params: Array<out Params>) -> Result): AsyncTask<Params, Progress, Result>() {
    var post: ((Result) -> Unit)? = null

    override fun onPostExecute(result: Result) {
        post?.invoke(result)
    }

    override fun doInBackground(vararg params: Params): Result {
        return background(params)
    }

    fun executeAndThen(postAction: ((Result) -> Unit)) {
        require(post == null)
        post = postAction
        execute()
    }

    fun executeAndThen(postAction: ((Result) -> Unit), vararg params: Params) {
        require(post == null)
        post = postAction
        execute(*params)
    }
}

class AsyncTaskEzAny(background: () -> Unit): AsyncTaskEz<Any, Any, Any>({ background() }
) {
}