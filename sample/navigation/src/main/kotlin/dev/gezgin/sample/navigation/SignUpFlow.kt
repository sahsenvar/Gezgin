package dev.gezgin.sample.navigation

import dev.gezgin.core.annotation.BackToStart
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.Quit
import dev.gezgin.core.annotation.QuitAndGoTo
import dev.gezgin.core.annotation.StartDestination

@FlowGraph
sealed interface SignUpFlow : AuthGraph {

  @StartDestination
  @GoTo(ProfileInfoScreenRoute::class)
  data object CredentialsScreenRoute : SignUpFlow

  @GoTo(TermsScreenRoute::class) data class ProfileInfoScreenRoute(val email: String) : SignUpFlow

  @BackToStart
  @Quit
  @QuitAndGoTo(HomeGraph.WelcomeScreenRoute::class)
  data object TermsScreenRoute : SignUpFlow
}
