package org.cikit.modules.github

import javax.crypto.spec.SecretKeySpec

data class GithubConfig(
        val key: String? = null,
        val username: String? = null,
        val password: String? = null) {

    val keySpec = key?.let {
        SecretKeySpec(it.toByteArray(), GithubHandler.SIGNATURE_ALGORITHM)
    }

}