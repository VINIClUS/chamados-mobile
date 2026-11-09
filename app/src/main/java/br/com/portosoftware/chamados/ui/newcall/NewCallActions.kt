package br.com.portosoftware.chamados.ui.newcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.portosoftware.chamados.core.model.Applicant
import br.com.portosoftware.chamados.core.model.CallPriority
import br.com.portosoftware.chamados.core.model.CallRules
import br.com.portosoftware.chamados.core.model.CallStatus
import br.com.portosoftware.chamados.ui.common.Banner
import br.com.portosoftware.chamados.ui.common.ErrorState
import br.com.portosoftware.chamados.ui.common.SelectField
import br.com.portosoftware.chamados.ui.common.SkeletonBox
import br.com.portosoftware.chamados.ui.common.StateMessage
import br.com.portosoftware.chamados.ui.common.UiState
import br.com.portosoftware.chamados.ui.common.colors
import br.com.portosoftware.chamados.ui.common.dataOrNull
import br.com.portosoftware.chamados.ui.common.formatBytes
import br.com.portosoftware.chamados.ui.common.icon
import br.com.portosoftware.chamados.ui.common.maskPhone
import br.com.portosoftware.chamados.ui.theme.Spacing

/** Ações do wizard, agrupadas para manter a assinatura das telas legível. */
data class NewCallActions(
    val onClose: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Boolean = { false },
    val onGoToStep: (WizardStep) -> Unit = {},
    val onApplicantQuery: (String) -> Unit = {},
    val onSelectApplicant: (Applicant) -> Unit = {},
    val onClearApplicant: () -> Unit = {},
    val onOpenQuickApplicant: () -> Unit = {},
    val onCloseQuickApplicant: () -> Unit = {},
    val onQuickApplicantChange: ((QuickApplicantForm) -> QuickApplicantForm) -> Unit = {},
    val onQuickApplicantUnit: (String?) -> Unit = {},
    val onCreateQuickPosition: () -> Unit = {},
    val onSaveQuickApplicant: () -> Unit = {},
    val onUnit: (String?) -> Unit = {},
    val onLocal: (String?) -> Unit = {},
    val onEquipment: (String?) -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onSubcategory: (String?) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onPriority: (CallPriority) -> Unit = {},
    val onStatus: (CallStatus) -> Unit = {},
    val onTechnician: (String?) -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onPickImages: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
)
