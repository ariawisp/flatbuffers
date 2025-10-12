rootProject.name = "flatc-kotlin"

include(
  ":core-ast",
  ":core-parser",
  ":core-semantics",
  ":reflection-writer",
  ":generator-common",
  ":generator-kotlin",
  ":generator-java",
  ":cli",
  ":compiler-plugin",
  ":compat",
)
