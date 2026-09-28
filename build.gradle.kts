plugins {
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}

// 키보드는 네트워크 권한이 없어야 한다 — 네트워크가 필요한 ML Kit은 별도 애드온 앱이 맡는다 (#25).
// 라이브러리가 병합해 오는 권한도 잡도록, 변형마다 병합된 매니페스트를 검사해 check와 assemble에 건다.
project(":app") {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                // 태스크 동작에는 variant 객체 대신 이름만 넘긴다 (구성 캐시에 담을 수 있게).
                val variantLabel = variant.name
                val variantName = variantLabel.replaceFirstChar { it.uppercase() }
                val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
                val marker = layout.buildDirectory.file("intermediates/no_network_permission/$variantLabel/verified")
                val verify = tasks.register("verify${variantName}NoNetworkPermission") {
                    group = "verification"
                    description = "Fails if the merged $variantLabel manifest requests a network permission."
                    inputs.file(manifest)
                    outputs.file(marker)
                    doLast {
                        val forbidden = setOf(
                            "android.permission.INTERNET",
                            "android.permission.ACCESS_NETWORK_STATE",
                        )
                        val androidNs = "http://schemas.android.com/apk/res/android"
                        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                            isNamespaceAware = true
                            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        }
                        val document = factory.newDocumentBuilder().parse(manifest.get().asFile)
                        val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
                            val nodes = document.getElementsByTagName(tag)
                            (0 until nodes.length).map {
                                (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name")
                            }
                        }
                        val found = requested.filter { it in forbidden }
                        if (found.isNotEmpty()) {
                            throw GradleException(
                                "The keyboard's merged $variantLabel manifest requests $found. The keyboard must " +
                                    "stay network-free: add tools:node=\"remove\" for it in app/src/main/AndroidManifest.xml.",
                            )
                        }
                        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
                    }
                }
                tasks.named("check") { dependsOn(verify) }
                // APK(assemble)와 AAB(bundle) 어느 쪽으로 만들어도 검사를 거친다.
                tasks.matching { it.name == "assemble$variantName" || it.name == "bundle$variantName" }
                    .configureEach { dependsOn(verify) }
            }
        }
    }
}
