sbt-guardrail [![sbt-guardrail](https://maven-badges.herokuapp.com/maven-central/dev.guardrail/sbt-guardrail/badge.svg)](https://search.maven.org/search?q=g:dev.guardrail%20a:sbt-guardrail) | [![Join the chat at https://gitter.im/guardrail-dev/guardrail](https://badges.gitter.im/guardrail-dev/guardrail.svg)](https://gitter.im/guardrail-dev/guardrail?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
=============


An SBT plugin for adding clients and servers generated by [guardrail](https://github.com/guardrail-dev/guardrail) to your service.

Installation
------------

Check out the [docs](https://github.com/guardrail-dev/guardrail/blob/master/docs/plugins/sbt.md)

### `project/plugins.sbt`
```
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "<Please use the latest available release!>")
```

### `build.sbt`
```scala
/* Available arguments:
    specPath: java.io.File
    pkg: String
    dto: String
    framework: String
    modules: List[String]
    tracing: Boolean
    imports: List[String]
    encodeOptionalAs: Option[CodingConfig]
    decodeOptionalAs: Option[CodingConfig]
*/
guardrailTasks in Compile := List(
  ScalaClient(file("petstore.yaml")),
  ScalaClient(file("github.yaml"), pkg="com.example.clients.github"),
  ScalaClient(file("github.yaml"), pkg="com.example.clients.github", 
              encodeOptionalAs = codingOptional,
              decodeOptionalAs = codingRequiredNullable),
  ScalaServer(file("myserver.yaml"), pkg="com.example.server", tracing=true),
  ScalaModels(file("myserver.yaml"), pkg="com.example.models"),
  JavaClient(file("github.yaml"), pkg="com.example.clients.github")
  ...
)
```
Alternatively use the `guardrailDiscoveredOpenApiFiles` setting to automatically discover OpenAPI spec files under `src/main/openapi` or `src/test/openapi` (see the [test for full example](src/sbt-test/sbt-guardrail/scala-client-codegen-app/build.sbt)):
```scala
Compile / guardrailTasks := (Compile / guardrailDiscoveredOpenApiFiles).value.flatMap { openApiFile =>
  List(          
    ScalaClient(openApiFile.file, pkg = openApiFile.pkg, framework = "http4s"),
    ScalaServer(openApiFile.file, pkg = openApiFile.pkg, framework = "http4s")
  )
}
```
