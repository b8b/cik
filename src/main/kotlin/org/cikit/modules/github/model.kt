package org.cikit.modules.github

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.ByteBuffer
import java.util.*

data class GithubError(
        val message: String?
)

data class GithubComment(
        val id: String,
        @JsonProperty("commit_id") val commitId: String,
        val body: String,
        val path: String?,
        val position: Int?
)

data class GithubContents(
        val name: String,
        val path: String,
        val size: Long?,
        val content: String
) {
    val contentBytes: ByteArray
        get() = Base64.getMimeDecoder().decode(content)
    val contentAsString: String
        get() = Charsets.UTF_8.decode(ByteBuffer.wrap(contentBytes)).toString()
}

data class GithubRepositoryPermissions(
        val admin: Boolean,
        val push: Boolean,
        val pull: Boolean
)

data class GithubUser(
        val id: Int,
        @JsonAlias("login") val name: String,
        val email: String?,
        val type: String?,
        @get:JsonProperty("avatar_url") val avatarUrl: String?
)

data class GithubRepository(
        val id: String,
        val name: String,
        @get:JsonProperty("full_name") val fullName: String,
        @get:JsonProperty("clone_url") val cloneUrl: String,
        @get:JsonProperty("ssh_url") val sshUrl: String,
        @get:JsonProperty("default_branch") val defaultBranch: String,
        val private: Boolean,
        val permissions: GithubRepositoryPermissions?,
        val owner: GithubUser
)

sealed class GithubHook() {
    abstract val repository: GithubRepository
    abstract val sender: GithubUser
}

data class GithubPushHook(
        override val repository: GithubRepository,
        override val sender: GithubUser,
        val ref: String,
        val before: String,
        val after: String,
        val created: Boolean,
        val deleted: Boolean,
        val forced: Boolean
) : GithubHook()
