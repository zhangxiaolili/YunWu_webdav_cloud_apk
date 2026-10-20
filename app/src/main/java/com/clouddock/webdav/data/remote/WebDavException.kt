package com.clouddock.webdav.data.remote

open class WebDavException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class WebDavHttpException(
    statusCode: Int,
    message: String,
    cause: Throwable? = null
) : WebDavException(message, statusCode, cause)

fun friendlyMessageForStatus(code: Int): String = when (code) {
    400 -> "请求格式不正确"
    401 -> "认证失败，请检查用户名和密码"
    403 -> "没有访问此路径的权限"
    404 -> "远程文件或文件夹不存在"
    405 -> "服务器不支持此操作"
    408 -> "服务器请求超时"
    409 -> "父文件夹不存在或目标发生冲突"
    412 -> "云端文件已被其他程序修改"
    423 -> "远程文件已被锁定"
    429 -> "请求过于频繁，请稍后再试"
    500 -> "WebDAV 服务器内部错误"
    502, 503, 504 -> "WebDAV 服务器暂时不可用"
    507 -> "云端存储空间不足"
    else -> "服务器返回 HTTP $code"
}
