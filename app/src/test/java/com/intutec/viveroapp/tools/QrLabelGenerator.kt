package com.intutec.viveroapp.tools

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.file.Files
import java.nio.file.Path

object QrLabelGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Uso: QrLabelGenerator <entrada.csv> <salida.html>" }
        val input = Path.of(args[0]).toAbsolutePath().normalize()
        val output = Path.of(args[1]).toAbsolutePath().normalize()
        val labels = Files.readAllLines(input)
            .drop(1)
            .filter(String::isNotBlank)
            .mapIndexed { index, line -> parseLabel(line, index + 2) }
        require(labels.isNotEmpty()) { "El CSV no contiene etiquetas." }

        output.parent?.let(Files::createDirectories)
        Files.writeString(output, renderPage(labels))
        println("Etiquetas QR generadas: $output")
    }

    private fun parseLabel(line: String, lineNumber: Int): Label {
        val fields = line.split(',', limit = 3).map(String::trim)
        require(fields.size == 3) {
            "La línea $lineNumber debe contener código, nombre y presentación."
        }
        val (code, name, presentation) = fields
        require(code.length in 2..128 && code.none(Char::isISOControl)) {
            "El código de la línea $lineNumber no es válido."
        }
        require(name.isNotBlank() && presentation.isNotBlank()) {
            "La línea $lineNumber requiere nombre y presentación."
        }
        return Label(code, name, presentation)
    }

    private fun renderPage(labels: List<Label>): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html lang=\"es\"><head><meta charset=\"utf-8\">")
        appendLine("<title>Etiquetas QR — Vivero Dulcinea</title>")
        appendLine("<style>")
        appendLine("@page{size:A4;margin:8mm}*{box-sizing:border-box}body{margin:0;font-family:Arial,sans-serif;color:#12372a}")
        appendLine(".sheet{display:grid;grid-template-columns:repeat(3,50mm);gap:4mm;align-items:start}")
        appendLine(".label{width:50mm;height:30mm;border:1px dashed #8ba397;border-radius:2mm;padding:2mm;display:flex;gap:2mm;break-inside:avoid}")
        appendLine("svg{width:24mm;height:24mm;flex:0 0 24mm}.copy{min-width:0;display:flex;flex-direction:column;justify-content:center}")
        appendLine(".brand{font-size:6pt;font-weight:700;color:#0b6b4b}.name{font-size:8pt;font-weight:700;line-height:1.1;margin-top:1mm}")
        appendLine(".presentation{font-size:6.5pt;margin-top:1mm}.code{font:700 6pt monospace;margin-top:1mm;overflow-wrap:anywhere}")
        appendLine("@media screen{body{padding:8mm;background:#eef3f0}.sheet{width:max-content;background:white;padding:8mm;box-shadow:0 2mm 8mm #0002}}")
        appendLine("</style></head><body><main class=\"sheet\">")
        labels.forEach { label ->
            appendLine("<article class=\"label\">")
            appendLine(renderQr(label.code))
            appendLine("<div class=\"copy\"><div class=\"brand\">VIVERO DULCINEA</div>")
            appendLine("<div class=\"name\">${label.name.escapeHtml()}</div>")
            appendLine("<div class=\"presentation\">${label.presentation.escapeHtml()}</div>")
            appendLine("<div class=\"code\">${label.code.escapeHtml()}</div></div></article>")
        }
        appendLine("</main></body></html>")
    }

    private fun renderQr(code: String): String {
        val matrix = QRCodeWriter().encode(
            code,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            ),
        )
        val path = buildString {
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) append("M$x $y" + "h1v1h-1z")
                }
            }
        }
        return "<svg role=\"img\" aria-label=\"QR ${code.escapeHtml()}\" " +
            "viewBox=\"0 0 ${matrix.width} ${matrix.height}\" shape-rendering=\"crispEdges\">" +
            "<rect width=\"100%\" height=\"100%\" fill=\"white\"/><path d=\"$path\" fill=\"black\"/></svg>"
    }

    private fun String.escapeHtml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private data class Label(
        val code: String,
        val name: String,
        val presentation: String,
    )
}
