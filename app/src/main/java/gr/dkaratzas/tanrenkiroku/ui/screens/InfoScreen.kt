package gr.dkaratzas.tanrenkiroku.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.dkaratzas.tanrenkiroku.R

// Composables for the info screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TANREN") },
                navigationIcon = { BackButton(onClick = onBack) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image
            Image(
                painter = painterResource(R.drawable.tanren),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
            )

            // Kanji block
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tan - \"To improve the quality of metal by hitting it from top to bottom.\"",
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ren - \"To improve the quality of metal by melting it and separating impure substances.\"",
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "From Kanjigen, 5th Revision. Translated by Masahiro Imafuji.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            InfoSection(
                title = "The Forge",
                body = "Together, these two characters describe an ancient forging process: heat iron, hammer it, fold it, repeat. A master swordsmith selects iron of varying carbon purity, works it through fire and force, and through sustained effort removes every impurity. What remains is a nihonto, a true Japanese blade, unbreakable precisely because it has been broken down and rebuilt."
            )

            InfoSection(
                title = "Tanren Is a Process of Self-Improvement",
                body = listOf(
                    "The forge is a metaphor. Before the hammer can do its work, impurities must first be identified and removed, and in human terms, that means letting go of preconceived ideas about what training should look like. Not all effort qualifies as tanren. Only training that demands genuine, sustained exertion carries that name.",
                    "\"Practice makes perfect\" is a comfortable myth. Only perfect practice makes perfect. Drilling without awareness simply reinforces what already exists, flaws included. To truly refine something, you must first know enough about it to recognise when it is wrong."
                )
            )

            InfoSection(
                title = "The Spiritual Forge",
                body = listOf(
                    "In budo, the ultimate goal of physical training is not physical at all. The punishing ordeal of repetition is not designed to sharpen the body, but to help the practitioner transcend it. Thousands of repetitions numb the analytical mind and free it from self-absorption.",
                    "Technique alone is inert. Theory alone is inert. United through the heat of real effort, they become something alive. As experience grows, so does discernment: you begin to see more clearly what must be refined, and exactly how. The forge never truly ends."
                )
            )

            InfoSection(
                title = "Your Gym, Your Forge",
                body = listOf(
                    "Every set you complete is a hammer strike. Every rep under load, every session you show up for when you would rather not, is the fire. The gym is a forge, and your body and mind are the metal being worked. Tanren is not a metaphor borrowed for inspiration, it is exactly what happens when you train with awareness and intent, week after week, year after year.",
                    "This app exists to give that process permanence. Tracking your sessions is not bureaucracy, it is discernment. Knowing which muscles you neglect, which lifts have stalled, which weeks you disappeared, is how you identify the impurities. You cannot refine what you do not record. TANREN Kiroku is your RECORD of the forge 記録, the ink that marks every strike of the hammer."
                )
            )

            // Kiroku image + version
            Image(
                painter = painterResource(R.drawable.kiroku),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = "TANREN Kiroku v$versionName",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Copyright
            Text(
                text = "© 2026 Dimitris Karatzas",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    InfoSection(title = title, body = listOf(body))
}

@Composable
private fun InfoSection(title: String, body: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        body.forEach { paragraph ->
            Text(
                text = paragraph,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
