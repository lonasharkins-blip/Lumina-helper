package com.lonasharkins.luminahelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonasharkins.luminahelper.accessibility.AccessibilityStatus
import com.lonasharkins.luminahelper.accessibility.LuminaAccessibilityService
import com.lonasharkins.luminahelper.midi.MidiParser
import com.lonasharkins.luminahelper.model.ImportedMidiFile
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.music.KeyLayoutFactory
import com.lonasharkins.luminahelper.playback.PlaybackPlanBuilder
import com.lonasharkins.luminahelper.playback.PlaybackMode
import com.lonasharkins.luminahelper.playback.PreparedPlayback
import com.lonasharkins.luminahelper.storage.InstrumentProfileRepository
import com.lonasharkins.luminahelper.storage.MidiLibraryRepository
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var profileRepository: InstrumentProfileRepository
    private lateinit var midiLibraryRepository: MidiLibraryRepository
    private lateinit var openMidiDocument: ActivityResultLauncher<Array<String>>
    private val midiExecutor = Executors.newSingleThreadExecutor()
    private var accessibilityEnabled by mutableStateOf(false)
    private var savedProfiles by mutableStateOf<List<InstrumentProfile>>(emptyList())
    private var importedMidiFiles by mutableStateOf<List<ImportedMidiFile>>(emptyList())
    private var selectedProfileId by mutableStateOf<String?>(null)
    private var isImportingMidi by mutableStateOf(false)
    private var isPreparingPlayback by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileRepository = InstrumentProfileRepository(this)
        midiLibraryRepository = MidiLibraryRepository(this)
        openMidiDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importMidi(uri)
        }
        enableEdgeToEdge()
        setContent {
            LuminaApp(
                accessibilityEnabled = accessibilityEnabled,
                savedProfiles = savedProfiles,
                importedMidiFiles = importedMidiFiles,
                selectedProfileId = selectedProfileId,
                isImportingMidi = isImportingMidi,
                isPreparingPlayback = isPreparingPlayback,
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onStartCalibration = ::startCalibration,
                onSelectProfile = { selectedProfileId = it },
                onChooseMidi = ::chooseMidi,
                onAssociateMidi = ::associateMidi,
                onPreparePlayback = ::preparePlayback,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = AccessibilityStatus.isEnabled(this)
        savedProfiles = profileRepository.loadAll()
        importedMidiFiles = midiLibraryRepository.loadAll()
        if (savedProfiles.none { it.id == selectedProfileId }) {
            selectedProfileId = savedProfiles.firstOrNull()?.id
        }
    }

    override fun onDestroy() {
        midiExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startCalibration(name: String, keyCount: Int) {
        val prepared = LuminaAccessibilityService.prepareCalibration(name, keyCount)
        if (!prepared) {
            Toast.makeText(
                this,
                "Ative novamente o serviço de acessibilidade e tente de novo",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        Toast.makeText(
            this,
            "Abra o jogo e toque no botão flutuante Mapear",
            Toast.LENGTH_LONG,
        ).show()
        moveTaskToBack(true)
    }

    private fun chooseMidi() {
        if (isImportingMidi) return
        openMidiDocument.launch(
            arrayOf(
                "audio/midi",
                "audio/x-midi",
                "application/x-midi",
                "application/octet-stream",
            ),
        )
    }

    private fun importMidi(uri: Uri) {
        if (isImportingMidi) return
        isImportingMidi = true
        val profileId = selectedProfileId

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        midiExecutor.execute {
            val result = runCatching {
                val bytes = readMidiBytes(uri)
                val song = MidiParser.parse(bytes)
                ImportedMidiFile.fromSong(
                    displayName = resolveDisplayName(uri),
                    uri = uri.toString(),
                    song = song,
                    instrumentProfileId = profileId,
                ).also(midiLibraryRepository::save)
            }

            runOnUiThread {
                isImportingMidi = false
                result.onSuccess { imported ->
                    importedMidiFiles = midiLibraryRepository.loadAll()
                    Toast.makeText(
                        this,
                        "${imported.noteCount} notas MIDI importadas",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: "Não foi possível ler este arquivo MIDI",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun readMidiBytes(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o arquivo selecionado")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MidiParser.MAX_FILE_SIZE_BYTES) {
                    error("O arquivo MIDI ultrapassa o limite de 8 MB")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val fromProvider = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Música MIDI"
    }

    private fun associateMidi(fileId: String, profileId: String) {
        midiLibraryRepository.associateProfile(fileId, profileId)
        importedMidiFiles = midiLibraryRepository.loadAll()
    }

    private fun preparePlayback(
        file: ImportedMidiFile,
        speedPercent: Int,
        transposeSemitones: Int,
        playbackMode: PlaybackMode,
    ) {
        if (isPreparingPlayback) return
        val profile = savedProfiles.firstOrNull { it.id == file.instrumentProfileId }
        if (profile == null) {
            Toast.makeText(this, "Associe um perfil calibrado a esta música", Toast.LENGTH_LONG).show()
            return
        }
        if (!AccessibilityStatus.isEnabled(this) || !LuminaAccessibilityService.isConnected()) {
            Toast.makeText(
                this,
                "Ative o serviço de acessibilidade antes de preparar a reprodução",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        isPreparingPlayback = true
        midiExecutor.execute {
            val result = runCatching {
                val song = MidiParser.parse(readMidiBytes(Uri.parse(file.uri)))
                val plan = PlaybackPlanBuilder.build(
                    song = song,
                    profile = profile,
                    speedPercent = speedPercent,
                    transposeSemitones = transposeSemitones,
                    playbackMode = playbackMode,
                )
                check(plan.events.isNotEmpty()) { "Este MIDI não possui notas reproduzíveis" }
                PreparedPlayback(
                    songName = file.songTitle?.takeIf { it.isNotBlank() } ?: file.displayName,
                    profileName = profile.name,
                    speedPercent = speedPercent,
                    transposeSemitones = transposeSemitones,
                    playbackMode = playbackMode,
                    plan = plan,
                )
            }

            runOnUiThread {
                isPreparingPlayback = false
                result.onSuccess { playback ->
                    if (LuminaAccessibilityService.preparePlayback(playback)) {
                        Toast.makeText(
                            this,
                            "No jogo, toque em Iniciar no controle flutuante",
                            Toast.LENGTH_LONG,
                        ).show()
                        moveTaskToBack(true)
                    } else {
                        Toast.makeText(
                            this,
                            "Não foi possível abrir os controles flutuantes",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: "Não foi possível preparar esta música",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}

private val LuminaColors = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF221149),
    secondary = Color(0xFF71E6D1),
    background = Color(0xFF0D0B14),
    surface = Color(0xFF171321),
    onBackground = Color(0xFFF5F0FF),
    onSurface = Color(0xFFF5F0FF),
)

@Composable
private fun LuminaApp(
    accessibilityEnabled: Boolean,
    savedProfiles: List<InstrumentProfile>,
    importedMidiFiles: List<ImportedMidiFile>,
    selectedProfileId: String?,
    isImportingMidi: Boolean,
    isPreparingPlayback: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartCalibration: (String, Int) -> Unit,
    onSelectProfile: (String) -> Unit,
    onChooseMidi: () -> Unit,
    onAssociateMidi: (String, String) -> Unit,
    onPreparePlayback: (ImportedMidiFile, Int, Int, PlaybackMode) -> Unit,
) {
    MaterialTheme(colorScheme = LuminaColors) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Lumina Helper",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Instrumentos de qualquer tamanho, mapeados do seu jeito.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    fontSize = 16.sp,
                )

                AccessibilityCard(
                    enabled = accessibilityEnabled,
                    onOpenSettings = onOpenAccessibilitySettings,
                )

                InstrumentBuilder(
                    accessibilityEnabled = accessibilityEnabled,
                    onStartCalibration = onStartCalibration,
                )

                if (savedProfiles.isNotEmpty()) {
                    SavedProfilesCard(savedProfiles)
                }

                MidiLibraryCard(
                    profiles = savedProfiles,
                    importedFiles = importedMidiFiles,
                    selectedProfileId = selectedProfileId,
                    isImporting = isImportingMidi,
                    isPreparingPlayback = isPreparingPlayback,
                    accessibilityEnabled = accessibilityEnabled,
                    onSelectProfile = onSelectProfile,
                    onChooseMidi = onChooseMidi,
                    onAssociateMidi = onAssociateMidi,
                    onPreparePlayback = onPreparePlayback,
                )
            }
        }
    }
}

@Composable
private fun AccessibilityCard(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            color = if (enabled) Color(0xFF55E69A) else Color(0xFFFFB05C),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (enabled) "Serviço de toque ativado" else "Serviço de toque desativado",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Você controla quando o Lumina pode tocar nas posições que forem calibradas.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (enabled) "Ver acessibilidade" else "Ativar acessibilidade")
            }
        }
    }
}

@Composable
private fun InstrumentBuilder(
    accessibilityEnabled: Boolean,
    onStartCalibration: (String, Int) -> Unit,
) {
    var profileName by rememberSaveable { mutableStateOf("Meu instrumento") }
    var keyCount by rememberSaveable { mutableIntStateOf(8) }
    val profile = remember(profileName, keyCount) {
        KeyLayoutFactory.centeredChromatic(
            id = "preview",
            name = profileName.ifBlank { "Instrumento personalizado" },
            keyCount = keyCount,
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Mapear novo instrumento",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Escolha quantas teclas existem e marque cada uma diretamente sobre o jogo. " +
                    "Você pode tocar nas brancas e nas pretas em qualquer ordem.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do instrumento") },
                singleLine = true,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { keyCount-- },
                    enabled = keyCount > 1,
                ) {
                    Text("−")
                }
                Text(
                    text = "$keyCount teclas",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { keyCount++ },
                    enabled = keyCount < 88,
                ) {
                    Text("+")
                }
            }

            InstrumentKeys(profile)

            Button(
                onClick = { onStartCalibration(profileName.trim(), keyCount) },
                enabled = accessibilityEnabled && profileName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mapear na tela")
            }

            Text(
                text = if (accessibilityEnabled) {
                    "O aplicativo irá para o fundo. No jogo, toque em Mapear e marque todas as " +
                        "teclas. O Lumina organiza automaticamente da esquerda para a direita."
                } else {
                    "Ative a acessibilidade acima antes de começar o mapeamento."
                },
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun InstrumentKeys(profile: InstrumentProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        profile.keys.chunked(5).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowKeys.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = key.label,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                repeat(5 - rowKeys.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SavedProfilesCard(profiles: List<InstrumentProfile>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Perfis salvos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            profiles.forEach { profile ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${profile.keys.size} teclas",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            text = "Calibrado",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MidiLibraryCard(
    profiles: List<InstrumentProfile>,
    importedFiles: List<ImportedMidiFile>,
    selectedProfileId: String?,
    isImporting: Boolean,
    isPreparingPlayback: Boolean,
    accessibilityEnabled: Boolean,
    onSelectProfile: (String) -> Unit,
    onChooseMidi: () -> Unit,
    onAssociateMidi: (String, String) -> Unit,
    onPreparePlayback: (ImportedMidiFile, Int, Int, PlaybackMode) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Importar música MIDI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Escolha um arquivo .mid ou .midi. O modo Melodia limpa escolhe uma voz " +
                    "principal e evita misturar todos os instrumentos.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            if (profiles.isEmpty()) {
                Text(
                    text = "Você pode importar agora, mas precisará calibrar um perfil para reproduzir depois.",
                    color = Color(0xFFFFC06A),
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = "Associar ao perfil",
                    fontWeight = FontWeight.SemiBold,
                )
                profiles.forEach { profile ->
                    if (profile.id == selectedProfileId) {
                        Button(
                            onClick = { onSelectProfile(profile.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Selecionado: ${profile.name}")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectProfile(profile.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(profile.name)
                        }
                    }
                }
            }

            Button(
                onClick = onChooseMidi,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isImporting) "Lendo arquivo..." else "Escolher arquivo MIDI")
            }

            if (importedFiles.isEmpty()) {
                Text(
                    text = "Nenhum arquivo MIDI importado.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Arquivos interpretados",
                    fontWeight = FontWeight.Bold,
                )
                importedFiles.forEach { file ->
                    ImportedMidiCard(
                        file = file,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        accessibilityEnabled = accessibilityEnabled,
                        isPreparingPlayback = isPreparingPlayback,
                        onAssociateMidi = onAssociateMidi,
                        onPreparePlayback = onPreparePlayback,
                    )
                }
            }

            Text(
                text = "A música só começa quando você tocar em Iniciar no controle flutuante sobre o jogo.",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ImportedMidiCard(
    file: ImportedMidiFile,
    profiles: List<InstrumentProfile>,
    selectedProfileId: String?,
    accessibilityEnabled: Boolean,
    isPreparingPlayback: Boolean,
    onAssociateMidi: (String, String) -> Unit,
    onPreparePlayback: (ImportedMidiFile, Int, Int, PlaybackMode) -> Unit,
) {
    val associatedProfile = profiles.firstOrNull { it.id == file.instrumentProfileId }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    var speedPercent by rememberSaveable(file.id) { mutableIntStateOf(100) }
    var transposeSemitones by rememberSaveable(file.id) { mutableIntStateOf(0) }
    var playbackMode by rememberSaveable(file.id) {
        mutableStateOf(PlaybackMode.CLEAN_MELODY)
    }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(file.displayName, fontWeight = FontWeight.SemiBold)
            if (!file.songTitle.isNullOrBlank() && file.songTitle != file.displayName) {
                Text(
                    text = file.songTitle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "${file.noteCount} notas • ${file.trackCount} faixas • ${formatDuration(file.durationMs)}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                fontSize = 13.sp,
            )
            if (file.lowestNote != null && file.highestNote != null) {
                Text(
                    text = "Extensão: ${midiNoteLabel(file.lowestNote)} até ${midiNoteLabel(file.highestNote)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            Text(
                text = associatedProfile?.let { "Perfil: ${it.name}" } ?: "Sem perfil associado",
                color = if (associatedProfile != null) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    Color(0xFFFFC06A)
                },
                fontSize = 13.sp,
            )
            Text(
                text = "MIDI formato ${file.format} • ${file.ticksPerQuarterNote} pulsos por tempo",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )

            if (file.trackCount > 1) {
                Text(
                    text = "Este MIDI possui várias faixas. Use Melodia limpa para evitar " +
                        "instrumentos sobrepostos.",
                    color = Color(0xFFFFC06A),
                    fontSize = 12.sp,
                )
            }

            if (selectedProfile != null && selectedProfile.id != associatedProfile?.id) {
                OutlinedButton(
                    onClick = { onAssociateMidi(file.id, selectedProfile.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Usar perfil: ${selectedProfile.name}")
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Modo de reprodução", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (playbackMode == PlaybackMode.CLEAN_MELODY) {
                Button(
                    onClick = { playbackMode = PlaybackMode.CLEAN_MELODY },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Selecionado: Melodia limpa")
                }
            } else {
                OutlinedButton(
                    onClick = { playbackMode = PlaybackMode.CLEAN_MELODY },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Melodia limpa")
                }
            }
            if (playbackMode == PlaybackMode.FULL_ARRANGEMENT) {
                Button(
                    onClick = { playbackMode = PlaybackMode.FULL_ARRANGEMENT },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Selecionado: Arranjo sem bateria")
                }
            } else {
                OutlinedButton(
                    onClick = { playbackMode = PlaybackMode.FULL_ARRANGEMENT },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Arranjo sem bateria")
                }
            }
            Text(
                text = if (playbackMode == PlaybackMode.CLEAN_MELODY) {
                    "Recomendado para pianos pequenos: toca uma nota principal por vez."
                } else {
                    "Mantém acordes e instrumentos, mas sempre ignora o canal de bateria."
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )

            Text("Velocidade", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { speedPercent -= 25 },
                    enabled = speedPercent > PlaybackPlanBuilder.MIN_SPEED_PERCENT,
                ) {
                    Text("−")
                }
                Text(
                    text = "$speedPercent%",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { speedPercent += 25 },
                    enabled = speedPercent < PlaybackPlanBuilder.MAX_SPEED_PERCENT,
                ) {
                    Text("+")
                }
            }

            Text("Transposição", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { transposeSemitones-- },
                    enabled = transposeSemitones > PlaybackPlanBuilder.MIN_TRANSPOSE,
                ) {
                    Text("−")
                }
                Text(
                    text = if (transposeSemitones >= 0) {
                        "+$transposeSemitones semitons"
                    } else {
                        "$transposeSemitones semitons"
                    },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { transposeSemitones++ },
                    enabled = transposeSemitones < PlaybackPlanBuilder.MAX_TRANSPOSE,
                ) {
                    Text("+")
                }
            }

            Button(
                onClick = {
                    onPreparePlayback(file, speedPercent, transposeSemitones, playbackMode)
                },
                enabled = accessibilityEnabled &&
                    associatedProfile != null &&
                    file.noteCount > 0 &&
                    !isPreparingPlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPreparingPlayback) "Preparando..." else "Abrir controles no jogo")
            }

            if (!accessibilityEnabled) {
                Text(
                    text = "Ative a acessibilidade para reproduzir.",
                    color = Color(0xFFFFC06A),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

private fun midiNoteLabel(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[note % 12]}${note / 12 - 1}"
}
