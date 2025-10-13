@file:JvmName("UtilsKt")

package org.jetbrains.kotlin.cli.common

import com.intellij.openapi.Disposable

/** Minimal stub for missing IntelliJ write-action helper in the test harness. */
fun disposeRootInWriteAction(disposable: Disposable) {
  disposable.dispose()
}
