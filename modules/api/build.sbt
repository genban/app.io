Common.settings

name := s"${Common.appName}.api"

scalacOptions += "-feature"

routesGenerator := InjectedRoutesGenerator

routesImport ++= Seq(
  "helpers.ExtBindable._",
  "helpers.Pager",
  "java.util.UUID",
  "models.cfs._",
  "org.joda.time.DateTime",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false