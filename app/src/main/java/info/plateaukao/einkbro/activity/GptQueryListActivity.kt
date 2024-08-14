package info.plateaukao.einkbro.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.R
import info.plateaukao.einkbro.database.ChatGptQuery
import info.plateaukao.einkbro.unit.BackupUnit
import info.plateaukao.einkbro.unit.BrowserUnit
import info.plateaukao.einkbro.unit.HelperUnit
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.viewmodel.GptQueryViewModel
import org.koin.core.component.KoinComponent
import java.text.SimpleDateFormat
import java.util.Locale

class GptQueryListActivity : ComponentActivity(), KoinComponent {
    private val gptQueryViewModel: GptQueryViewModel by viewModels()
    private val backupUnit: BackupUnit by lazy { BackupUnit(this) }

    private lateinit var exportGptQueriesLauncher: ActivityResultLauncher<Intent>

    private var highlightsRoute = HighlightsRoute.RouteArticles
    private fun showFileChooser(highlightsRoute: HighlightsRoute) {
        this.highlightsRoute = highlightsRoute
        BrowserUnit.createFilePicker(exportGptQueriesLauncher, "gpt_queries.html")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exportGptQueriesLauncher =
            IntentUnit.createResultLauncher(this) {
                //it.data?.data?.let { uri -> exportHighlights(uri) }
            }


        setContent {
            MyTheme {
                GptQueriesScreen(
                    gptQueryViewModel,
                    onLinkClick = {
                        IntentUnit.launchUrl(this, it.url)
                    }
                )
            }
        }
    }

//    private fun exportHighlights(uri: Uri) {
//        highlightViewModel.viewModelScope.launch(Dispatchers.IO) {
//            val data = if (highlightsRoute == HighlightsRoute.RouteArticles) {
//                highlightViewModel.dumpArticlesHighlights()
//            } else {
//                highlightViewModel.dumpSingleArticleHighlights(highlightsRoute.articleId)
//            }
//            backupUnit.exportHighlights(uri, data)
//
//            withContext(Dispatchers.Main) {
//                NinjaToast.show(this@HighlightsActivity, R.string.toast_backup_successful)
//                IntentUnit.showFile(this@HighlightsActivity, uri)
//            }
//        }
//    }

    companion object {
        fun createIntent(context: Context) = Intent(
            context,
            GptQueryListActivity::class.java
        )
    }
}

@Composable
fun GptQueriesScreen(
    gptQueryViewModel: GptQueryViewModel,
    onLinkClick: (ChatGptQuery) -> Unit
) {
    val gptQueries by gptQueryViewModel.getGptQueries().collectAsState(emptyList())
    LazyColumn(
        modifier = Modifier.padding(10.dp),
        reverseLayout = true
    ) {
        items(gptQueries.size) { index ->
            val gptQuery = gptQueries[index]
            QueryItem(
                modifier = Modifier.padding(vertical = 10.dp),
                gptQuery = gptQuery,
                onLinkClick = { onLinkClick(gptQuery) },
                deleteQuery = {
                    gptQueryViewModel.deleteGptQuery(gptQuery)
                }
            )
            if (index < gptQueries.lastIndex) Divider(thickness = 1.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueryItem(
    modifier: Modifier,
    gptQuery: ChatGptQuery,
    onLinkClick: () -> Unit = {},
    deleteQuery: (ChatGptQuery) -> Unit
) {
    // use a remember to toggle result widget visibility
    var showResult by remember { mutableStateOf(false) }
    val queryString = if (gptQuery.selectedText.contains("<<") &&
        gptQuery.selectedText.contains(">>")) {
        HelperUnit.parseMarkdown(gptQuery.selectedText.replace("<<", "**").replace(">>", "**"))
    } else {
        AnnotatedString(gptQuery.selectedText)
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    showResult = !showResult
                },
                onLongClick = {
                    deleteQuery(gptQuery)
                }
            )
    ) {
        Text(
            modifier = modifier,
            text = queryString,
            color = MaterialTheme.colors.onPrimary
        )
        if (showResult) {
            Text(
                modifier = modifier,
                text = HelperUnit.parseMarkdown(gptQuery.result),
                color = MaterialTheme.colors.onPrimary
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onLinkClick() },
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(id = R.drawable.icon_exit),
                contentDescription = "link",
                tint = MaterialTheme.colors.onPrimary
            )
            Text(
                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(gptQuery.date),
                style = MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.onPrimary
                )
            )
        }
    }
}

@Preview
@Composable
fun PreviewQueryItem() {
    MyTheme {
        QueryItem(
            modifier = Modifier.padding(10.dp),
            gptQuery = ChatGptQuery(
                selectedText = "selected text",
                result = "result",
                date = System.currentTimeMillis(),
                url = "https://example.com",
                model = "model",
            ),
            onLinkClick = {},
            deleteQuery = {}
        )
    }
}
