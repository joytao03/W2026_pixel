package com.example.cpen321application

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RANDOM_MEAL_URL = "https://www.themealdb.com/api/json/v1/1/random.php"
private const val MAX_RECIPE_BYTES = 512 * 1024
private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

internal data class MealRecipe(
    val id: String,
    val name: String,
    val category: String,
    val area: String,
    val imageUrl: String,
    val ingredients: List<String>,
    val instructions: String,
)

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

internal fun parseMealRecipe(raw: String): MealRecipe {
    val meal = JSONObject(raw).optJSONArray("meals")?.optJSONObject(0)
        ?: throw IllegalArgumentException("No recipe was returned. Please try again.")
    val name = meal.text("strMeal")
    require(name.isNotBlank()) { "The recipe has no name. Please try again." }
    val ingredients = (1..20).mapNotNull { index ->
        val ingredient = meal.text("strIngredient$index")
        if (ingredient.isBlank()) null
        else listOf(meal.text("strMeasure$index"), ingredient).filter { it.isNotBlank() }.joinToString(" ")
    }
    return MealRecipe(meal.text("idMeal"), name, meal.text("strCategory"), meal.text("strArea"),
        meal.text("strMealThumb"), ingredients, meal.text("strInstructions"))
}

@Composable
internal fun MealSurpriseScreen(onSetTimer: () -> Unit) {
    var rawRecipe by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    val uriHandler = LocalUriHandler.current
    val recipe = remember(rawRecipe) { rawRecipe?.let { runCatching { parseMealRecipe(it) }.getOrNull() } }

    LaunchedEffect(retryKey) {
        // Keep the fetched recipe across rotation instead of making another request.
        if (rawRecipe == null) {
            loading = true
            error = null
            try {
                rawRecipe = withContext(Dispatchers.IO) {
                    val raw = readMealBytes(RANDOM_MEAL_URL, MAX_RECIPE_BYTES).toString(Charsets.UTF_8)
                    parseMealRecipe(raw)
                    raw
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "We couldn't load a recipe. Check your internet connection and try again." }
            finally { loading = false }
        }
    }

    Text("Time for something tasty!", style = MaterialTheme.typography.headlineMedium)
    Text("Your countdown is over. Here's a random meal to try.")
    if (loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Finding your surprise recipe...")
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    if (recipe != null) {
        MealPhoto(recipe.imageUrl, recipe.name)
        Text(recipe.name, style = MaterialTheme.typography.headlineSmall)
        val details = listOf(recipe.area, recipe.category).filter { it.isNotBlank() }.joinToString(" / ")
        if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.primary)
    }
    // Controls remain near the top even when the recipe has long instructions.
    Button(enabled = !loading, onClick = { rawRecipe = null; retryKey++ }) {
        Text(if (error != null) "Try again" else "Surprise me with another meal")
    }
    OutlinedButton(onClick = onSetTimer) { Text("Set another timer") }
    if (recipe != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ingredients", style = MaterialTheme.typography.titleLarge)
                if (recipe.ingredients.isEmpty()) Text("Ingredients were not supplied by TheMealDB.")
                recipe.ingredients.forEach { Text("\u2022 $it") }
            }
        }
        Text("Recipe", style = MaterialTheme.typography.titleLarge)
        Text(recipe.instructions.ifBlank { "Instructions were not supplied by TheMealDB." })
    }
    Text("Recipe and photo provided by TheMealDB.", style = MaterialTheme.typography.bodySmall)
    var linkError by remember { mutableStateOf(false) }
    TextButton(onClick = {
        val url = if (recipe?.id?.matches(Regex("[0-9]+")) == true)
            "https://www.themealdb.com/meal/${recipe.id}" else "https://www.themealdb.com/"
        linkError = runCatching { uriHandler.openUri(url) }.isFailure
    }) { Text("View on TheMealDB") }
    if (linkError) Text("No browser is available to open the recipe.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MealPhoto(imageUrl: String, name: String) {
    var image by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var finished by remember(imageUrl) { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        try {
            image = withContext(Dispatchers.IO) {
                val bytes = readMealBytes(imageUrl, MAX_IMAGE_BYTES)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 1000 || bounds.outHeight / sample > 1000) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { image = null }
        finally { finished = true }
    }
    val bitmap = image
    if (bitmap != null) {
        Card(Modifier.fillMaxWidth()) {
            Image(bitmap, contentDescription = name, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Crop)
        }
    } else if (finished) {
        Text("Photo unavailable - the recipe is still shown below.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun readMealBytes(address: String, maximum: Int): ByteArray {
    val url = URL(address)
    require(url.protocol == "https" && url.host in setOf("www.themealdb.com", "themealdb.com"))
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        check(connection.responseCode in 200..299) { "TheMealDB request failed." }
        return connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= maximum) { "Response is too large." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally { connection.disconnect() }
}
