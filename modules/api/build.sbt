Common.settings

name := s"${Common.appName}.api"

scalacOptions += "-feature"

routesGenerator := InjectedRoutesGenerator

libraryDependencies += "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.0"

routesImport ++= Seq(
  "elasticsearch.SortField",
  "helpers.ExtBindable._",
  "java.util.UUID",
  "models.cfs._",
  "models.misc._",
  "org.joda.time._",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false