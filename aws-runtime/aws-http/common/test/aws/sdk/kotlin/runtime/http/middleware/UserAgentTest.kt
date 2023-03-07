/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.sdk.kotlin.runtime.http.middleware

import aws.sdk.kotlin.runtime.http.ApiMetadata
import aws.sdk.kotlin.runtime.http.loadAwsUserAgentMetadataFromEnvironment
import aws.sdk.kotlin.runtime.http.operation.customUserAgentMetadata
import aws.sdk.kotlin.runtime.testing.TestPlatformProvider
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.PlatformProvider
import aws.smithy.kotlin.runtime.util.get
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserAgentTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.fromValue(200), Headers.Empty, HttpBody.Empty)
            val now = Instant.now()
            return HttpCall(request, resp, now, now)
        }
    }

    private val client = SdkHttpClient(mockEngine)

    private fun initializeOp(platformProvider: PlatformProvider = TestPlatformProvider()) =
        SdkHttpOperation.build<Unit, HttpResponse> {
            serializer = UnitSerializer
            deserializer = IdentityDeserializer
            context {
                operationName = "testOperation"
            }
        }.apply {
            val apiMd = ApiMetadata("Test Service", "1.2.3")
            val metadata = loadAwsUserAgentMetadataFromEnvironment(platformProvider, apiMd)
            install(UserAgent(metadata))
        }

    @Test
    fun itSetsUAHeaders() = runTest {
        val op = initializeOp()

        op.roundTrip(client, Unit)
        val request = op.context[HttpOperationContext.HttpCallList].last().request
        assertTrue(request.headers.contains(USER_AGENT))
        assertTrue(request.headers.contains(X_AMZ_USER_AGENT))
        assertEquals("aws-sdk-kotlin/1.2.3", request.headers[X_AMZ_USER_AGENT])
        // FIXME re-enable once user agent strings can be longer
        // assertTrue(request.headers[USER_AGENT]!!.startsWith("aws-sdk-kotlin/1.2.3 api/test-service/1.2.3"))
        assertTrue(request.headers[USER_AGENT]!!.startsWith("aws-sdk-kotlin/1.2.3"))
    }

    @Test
    fun itAddsPerOperationMetadata() = runTest {
        val op = initializeOp()
        op.context.customUserAgentMetadata.add("foo", "bar")

        op.roundTrip(client, Unit)
        val request = op.context[HttpOperationContext.HttpCallList].last().request

        request.headers[USER_AGENT]!!.shouldContain("md/foo/bar")

        // verify per/request metadata is actually per/request
        val op2 = initializeOp()
        op2.context.customUserAgentMetadata.add("baz", "quux")

        op2.roundTrip(client, Unit)
        val request2 = op2.context[HttpOperationContext.HttpCallList].last().request

        request2.headers[USER_AGENT]!!.shouldNotContain("md/foo/bar")
        request2.headers[USER_AGENT]!!.shouldContain("md/baz/quux")
    }

    @Test
    fun itMergesCustomMetadataWithExisting() = runTest {
        // see: https://github.com/awslabs/aws-sdk-kotlin/issues/694
        val platform = TestPlatformProvider(
            props = mapOf(
                "aws.customMetadata.foo" to "bar",
                "aws.customMetadata.baz" to "qux",
            ),
        )
        val op = initializeOp(platform)
        op.context.customUserAgentMetadata.apply {
            add("baz", "quux")
            add("blerg", "blarg")
        }

        op.roundTrip(client, Unit)
        val request = op.context[HttpOperationContext.HttpCallList].last().request
        val uaString = request.headers[USER_AGENT]!!

        uaString.shouldContain("md/foo/bar")
        uaString.shouldContain("md/baz/quux")
        uaString.shouldContain("md/blerg/blarg")
        uaString.shouldNotContain("md/baz/qux") // This was overwritten by "baz/quux"
    }

    @Test
    fun itDoesNotClobberExistingCustomMetadata() = runTest {
        // see: https://github.com/awslabs/aws-sdk-kotlin/issues/694
        val platform = TestPlatformProvider(
            props = mapOf(
                "aws.customMetadata.foo" to "bar",
                "aws.customMetadata.baz" to "qux",
            ),
        )
        val op = initializeOp(platform)

        op.roundTrip(client, Unit)
        val request = op.context[HttpOperationContext.HttpCallList].last().request
        val uaString = request.headers[USER_AGENT]!!

        uaString.shouldContain("md/foo/bar")
        uaString.shouldContain("md/baz/qux")
    }
}
