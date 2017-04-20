package org.cikit.modules.github

class GithubException(msg: String) : Exception(msg) {
    constructor(statusCode: Int, error: GithubError) : this("received status code $statusCode: ${error.message}")
}