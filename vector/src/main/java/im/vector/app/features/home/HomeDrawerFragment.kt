/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.extensions.CabinetActivity
import im.vector.app.core.extensions.observeK
import im.vector.app.core.extensions.replaceChildFragment
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.utils.startSharePlainTextIntent
import im.vector.app.databinding.FragmentHomeDrawerBinding
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.app.features.permalink.PermalinkFactory
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsActivity
import im.vector.app.features.spaces.SpaceListFragment
import im.vector.app.features.usercode.UserCodeActivity
import im.vector.app.features.workers.signout.SignOutUiWorker
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

@AndroidEntryPoint
class HomeDrawerFragment :
        VectorBaseFragment<FragmentHomeDrawerBinding>() {

    @Inject lateinit var session: Session
    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var buildMeta: BuildMeta
    @Inject lateinit var permalinkFactory: PermalinkFactory

    private lateinit var sharedActionViewModel: HomeSharedActionViewModel

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeDrawerBinding {
        return FragmentHomeDrawerBinding.inflate(inflater, container, false)
    }

    @SuppressLint("StringFormatInvalid")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedActionViewModel = activityViewModelProvider.get(HomeSharedActionViewModel::class.java)

        if (savedInstanceState == null) {
            replaceChildFragment(R.id.homeDrawerGroupListContainer, SpaceListFragment::class.java)
        }
        session.userService().getUserLive(session.myUserId).observeK(viewLifecycleOwner) { optionalUser ->
            val user = optionalUser?.getOrNull()
            if (user != null) {
                avatarRenderer.render(user.toMatrixItem(), views.homeDrawerHeaderAvatarView)
                views.homeDrawerUsernameView.text = user.displayName
                views.homeDrawerUserIdView.text = user.userId
            }
        }
        // Profile
        views.homeDrawerHeader.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openSettings(requireActivity(), directAccess = VectorSettingsActivity.EXTRA_DIRECT_ACCESS_GENERAL)
        }
        // Settings
        views.homeDrawerHeaderSettingsView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openSettings(requireActivity())
        }
        // Sign out
        views.homeDrawerHeaderSignoutView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            SignOutUiWorker(requireActivity()).perform()
        }

        views.layoutCabinet.setOnClickListener {
            startActivity(Intent(context, CabinetActivity::class.java))
        }


        views.homeDrawerQRCodeButton.debouncedClicks {
            UserCodeActivity.newIntent(requireContext(), sharedActionViewModel.session.myUserId).let {
                val options =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                requireActivity(),
                                views.homeDrawerHeaderAvatarView,
                                ViewCompat.getTransitionName(views.homeDrawerHeaderAvatarView) ?: ""
                        )
                startActivity(it, options.toBundle())
            }
        }

        views.homeDrawerInviteFriendButton.debouncedClicks {
            analyticsTracker.screen(MobileScreen(screenName = MobileScreen.ScreenName.InviteFriends))
            val text = getString(R.string.invite_friends_text)

            startSharePlainTextIntent(
                    context = requireContext(),
                    activityResultLauncher = null,
                    chooserTitle = getString(R.string.invite_friends),
                    text = text,
                    extraTitle = getString(R.string.invite_friends_rich_title)
            )
        }

        // Debug menu
        views.homeDrawerHeaderDebugView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.CloseDrawer)
            navigator.openDebug(requireActivity())
        }
    }

    override fun onResume() {
        super.onResume()
        views.homeDrawerHeaderDebugView.isVisible = buildMeta.isDebug && vectorPreferences.developerMode()
    }
}