package com.copperleaf.ballast.examples.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.copperleaf.ballast.navigation.routing.Backstack
import com.copperleaf.ballast.navigation.routing.RouterContract
import com.copperleaf.ballast.navigation.routing.build
import com.copperleaf.ballast.navigation.routing.directions
import com.copperleaf.ballast.navigation.routing.intPath
import com.copperleaf.ballast.navigation.routing.optionalStringQuery
import com.copperleaf.ballast.navigation.routing.pathParameter
import com.copperleaf.ballast.navigation.routing.queryParameter
import com.copperleaf.ballast.navigation.routing.renderCurrentDestination
import com.copperleaf.ballast.navigation.vm.Router

@ExperimentalMaterial3Api
object NavigationUi {

    @Composable
    fun Content() {
        val applicationScope = rememberCoroutineScope()
        val router: Router<AppScreen> = remember(applicationScope) { createRouter(applicationScope) }

        val routerState: Backstack<AppScreen> by router.observeStates().collectAsState()

        Column {
            routerState.renderCurrentDestination(
                route = { appScreen: AppScreen ->
                    when (appScreen) {
                        AppScreen.Home -> {
                            Home(
                                goToPostList = {
                                    router.trySend(
                                        RouterContract.Inputs.GoToDestination(
                                            AppScreen.PostList.directions().build()
                                        )
                                    )
                                },
                                goToPost = { postId ->
                                    router.trySend(
                                        RouterContract.Inputs.GoToDestination(
                                            AppScreen.PostDetails.directions()
                                                .pathParameter("postId", postId.toString())
                                                .build()
                                        )
                                    )
                                }
                            )
                        }

                        AppScreen.PostList -> {
                            val sort by optionalStringQuery()
                            PostList(
                                sortDirection = sort,
                                changeSort = { sortDirection ->
                                    router.trySend(
                                        RouterContract.Inputs.ReplaceTopDestination(
                                            AppScreen.PostList.directions().queryParameter("sort", sortDirection)
                                                .build()
                                        )
                                    )
                                },
                                goBack = {
                                    router.trySend(
                                        RouterContract.Inputs.GoBack()
                                    )
                                },
                                goToPost = { postId ->
                                    router.trySend(
                                        RouterContract.Inputs.GoToDestination(
                                            AppScreen.PostDetails.directions()
                                                .pathParameter("postId", postId.toString())
                                                .build()
                                        )
                                    )
                                },
                            )
                        }

                        AppScreen.PostDetails -> {
                            val postId by intPath()
                            PostDetails(
                                postId = postId,
                                goBack = {
                                    router.trySend(
                                        RouterContract.Inputs.GoBack()
                                    )
                                },
                            )
                        }
                    }
                },
                notFound = { },
            )

            Divider()
            Text("Backstack")

            routerState
                .withIndex()
                .reversed()
                .forEach { (index, destination) ->
                    Text("- [$index] ${destination.originalDestinationUrl}")
                }
        }
    }

    @Composable
    fun Home(
        goToPostList: () -> Unit,
        goToPost: (postId: Int) -> Unit,
    ) {
        Column {
            Text("Home")

            Button({ goToPostList() }) {
                Text("Go To Post List")
            }

            Button({ goToPost(5) }) {
                Text("Go To Latest Post")
            }
        }
    }

    @Composable
    fun PostList(
        sortDirection: String?,
        changeSort: (String) -> Unit,
        goBack: () -> Unit,
        goToPost: (postId: Int) -> Unit,
    ) {
        Column {
            Text("Post List")

            Button({ goBack() }) {
                Text("Go Back")
            }

            if (sortDirection == "asc") {
                Button({ changeSort("desc") }) {
                    Text("Sort Descending")
                }
            } else {
                Button({ changeSort("asc") }) {
                    Text("Sort Ascending")
                }
            }

            val posts = if (sortDirection == "asc") {
                (1..5)
            } else {
                (1..5).reversed()
            }

            posts.forEach { index ->
                Button({ goToPost(index) }) {
                    Text("Go To Post $index")
                }
            }
        }
    }

    @Composable
    fun PostDetails(
        postId: Int,
        goBack: () -> Unit,
    ) {
        Column {
            Text("Post $postId")

            Button({ goBack() }) {
                Text("Go Back")
            }
        }
    }
}
