Common.settings

name := s"${Common.appName}"

scalacOptions += "-feature"

LessKeys.compress in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

pipelineStages := Seq(uglify, gzip)

lazy val models =
  (project in file("modules/models"))
    .enablePlugins(PlayScala)

lazy val security =
  (project in file("modules/security"))
    .enablePlugins(PlayScala)
    .dependsOn(models)
    .aggregate(models)

lazy val services =
  (project in file("modules/services"))
    .enablePlugins(PlayScala)
    .dependsOn(models % "test->test;compile->compile")
    .aggregate(models)

lazy val api =
  (project in file("modules/api"))
    .enablePlugins(PlayScala)
    .dependsOn(security, services)
    .aggregate(security, services)

lazy val root =
  (project in file("."))
    .enablePlugins(PlayScala)
    .dependsOn(api)
    .aggregate(api)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  filters,
  "com.mohiva"        %% "play-html-compressor"       % "0.6.1",
  "org.webjars.bower" %  "bootstrap"                  % "3.3.6",
  "org.webjars.bower" %  "font-awesome"               % "4.4.0",
  "org.webjars.bower" %  "holderjs"                   % "2.6.0",
  "org.webjars.bower" %  "fuelux"                     % "3.10.0",
  "org.webjars.bower" %  "angular-xeditable"          % "0.1.9",
  "org.webjars.bower" %  "angular-bootstrap"          % "1.3.3",
  "org.webjars.bower" %  "angular-resource"           % "1.4.4",
  "org.webjars.bower" %  "angular-animate"            % "1.4.4",
  "org.webjars.bower" %  "angular-sanitize"           % "1.4.4",
  "org.webjars.bower" %  "angular-i18n"               % "1.4.4",
  "org.webjars.bower" %  "angular-cookies"            % "1.4.4",
  "org.webjars.bower" %  "underscore"                 % "1.8.3",
  "org.webjars.bower" %  "animate.css"                % "3.4.0",
  "org.webjars.bower" %  "ng-flow"                    % "2.6.1",
  "org.webjars.bower" %  "metisMenu"                  % "2.5.0",
  "org.webjars.bower" %  "pace"                       % "1.0.2",
  "org.webjars.bower" %  "awesome-bootstrap-checkbox" % "0.3.7",
  "org.webjars.bower" %  "sweetalert2"                % "4.0.8",
  "com.codeborne"     %  "phantomjsdriver"            % "1.2.1"   % Test
)

dependencyOverrides ++= Set(
 "org.webjars.bower" % "angular" % "1.4.4",
 "org.webjars.bower" % "jquery"  % "2.2.4"
)

libraryDependencies += specs2 % Test

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

TwirlKeys.templateImports ++= Seq(
  "elasticsearch.SortField",
  "helpers.ExtDateTimeFormat._",
  "helpers.syntax._",
  "java.util.UUID",
  "models.cfs._",
  "models.misc._",
  "org.joda.time._",
  "play.api.Environment",
  "play.api.i18n.{Messages => MSG}",
  "scala.util._",
  "security._"
)

routesImport ++= Seq(
  "elasticsearch.SortField",
  "helpers.ExtBindable._",
  "java.util.UUID",
  "models.cfs._",
  "models.misc._",
  "org.joda.time.DateTime",
  "play.api.i18n.Lang",
  "scala.language.reflectiveCalls"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false