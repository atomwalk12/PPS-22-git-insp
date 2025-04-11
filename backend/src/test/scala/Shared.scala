package gitinsp.tests

import org.scalatest.Tag

object ExternalService extends Tag("gitinsp.tests.tags.ExternalService")
object Integration     extends Tag("gitinsp.tests.tags.Integration")
object Slow            extends Tag("gitinsp.tests.tags.Slow")
object HandleErrors    extends Tag("gitinsp.tests.tags.HandleErrors")

val ENABLE_LOGGING = false
val storeName      = "github.com[slash]atomwalk12[slash]PPS-22-git-insp-code"
val repoName       = "https://github.com/atomwalk12/PPS-22-git-insp"

val externalServiceTag = ExternalService
