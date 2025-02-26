/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "AWS client runtime core"
extra["displayName"] = "AWS :: SDK :: Kotlin :: Client Runtime"
extra["moduleName"] = "aws.sdk.kotlin.runtime"

val smithyKotlinVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api("aws.smithy.kotlin:runtime-core:$smithyKotlinVersion")
                api("aws.smithy.kotlin:smithy-client:$smithyKotlinVersion")
                implementation("aws.smithy.kotlin:logging:$smithyKotlinVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
