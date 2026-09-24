package com.example.export

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.DutyAssignment
import com.example.data.model.DutyPost
import com.example.data.model.Employee
import com.example.domain.DutyRulesEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object ScheduleExportManager {

    private val ruLocale = Locale("ru", "RU")

    /**
     * Создает PDF файл графика дежурств
     */
    fun createPdf(
        context: Context,
        startDate: LocalDate,
        endDate: LocalDate,
        assignments: List<DutyAssignment>,
        employees: List<Employee>,
        posts: List<DutyPost>,
        highlightChanges: Boolean = false,
        baselineKeys: Set<String> = emptySet()
    ): File {
        val empMap = employees.associateBy { it.id }
        val assignmentsMap = assignments.groupBy { "${it.dateString}_${it.postId}_${it.slotIndex}" }

        val pdfDocument = PdfDocument()

        // Стандартная альбомная страница A4: 842 x 595 точек (72 dpi)
        val pageWidth = 842
        val pageHeight = 595
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        drawScheduleDocument(
            canvas = canvas,
            width = pageWidth.toFloat(),
            height = pageHeight.toFloat(),
            startDate = startDate,
            endDate = endDate,
            assignmentsMap = assignmentsMap,
            empMap = empMap,
            posts = posts,
            isPdf = true,
            highlightChanges = highlightChanges,
            baselineKeys = baselineKeys
        )

        pdfDocument.finishPage(page)

        val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(outputDir, "График_нарядов_${startDate}_${endDate}.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        fos.close()
        pdfDocument.close()

        return file
    }

    /**
     * Создает изображение PNG графика дежурств высокой четкости
     */
    fun createImage(
        context: Context,
        startDate: LocalDate,
        endDate: LocalDate,
        assignments: List<DutyAssignment>,
        employees: List<Employee>,
        posts: List<DutyPost>,
        highlightChanges: Boolean = false,
        baselineKeys: Set<String> = emptySet()
    ): File {
        val empMap = employees.associateBy { it.id }
        val assignmentsMap = assignments.groupBy { "${it.dateString}_${it.postId}_${it.slotIndex}" }

        // Высокое разрешение для мессенджеров и печати: 1684 x 1190 пикселей (2x A4)
        val imgWidth = 1684
        val imgHeight = 1190

        val bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawScheduleDocument(
            canvas = canvas,
            width = imgWidth.toFloat(),
            height = imgHeight.toFloat(),
            startDate = startDate,
            endDate = endDate,
            assignmentsMap = assignmentsMap,
            empMap = empMap,
            posts = posts,
            isPdf = false,
            highlightChanges = highlightChanges,
            baselineKeys = baselineKeys
        )

        val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(outputDir, "График_нарядов_${startDate}_${endDate}.png")
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.close()

        return file
    }

    /**
     * Отрисовка таблицы графика на канвасе
     */
    private fun drawScheduleDocument(
        canvas: Canvas,
        width: Float,
        height: Float,
        startDate: LocalDate,
        endDate: LocalDate,
        assignmentsMap: Map<String, List<DutyAssignment>>,
        empMap: Map<Long, Employee>,
        posts: List<DutyPost>,
        isPdf: Boolean,
        highlightChanges: Boolean = false,
        baselineKeys: Set<String> = emptySet()
    ) {
        val scale = if (isPdf) 1.0f else 2.0f

        // Фон страницы
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        val margin = 24f * scale
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 30, 48)
            textSize = 15f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 80, 95)
            textSize = 10f * scale
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        // Заголовок документа
        val titleY = margin + 14f * scale
        canvas.drawText("ГРАФИК СУТОЧНЫХ ДЕЖУРСТВ И НАРЯДОВ", width / 2f, titleY, titlePaint)

        val periodText = "Период: с ${startDate.dayOfMonth} ${startDate.month.getDisplayName(TextStyle.FULL, ruLocale)} ${startDate.year}г. " +
                "по ${endDate.dayOfMonth} ${endDate.month.getDisplayName(TextStyle.FULL, ruLocale)} ${endDate.year}г."
        canvas.drawText(periodText, width / 2f, titleY + 16f * scale, subTitlePaint)

        if (highlightChanges) {
            val changeNoticePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(180, 40, 20)
                textSize = 8.5f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("● Оранжевым выделены изменения в графике", width - margin, titleY + 16f * scale, changeNoticePaint)
        }

        // Размеры таблицы
        val tableTop = titleY + 30f * scale
        val tableBottom = height - (36f * scale)
        val tableLeft = margin
        val tableRight = width - margin
        val tableWidth = tableRight - tableLeft

        // Колонки: Дата (12%), КПП-1 (30%), КПП-2 (18%), Ст. машины (24%), ВГ-2 (16%)
        val colWidths = floatArrayOf(
            tableWidth * 0.12f, // Дата / день
            tableWidth * 0.30f, // КПП 1 (3 чел)
            tableWidth * 0.18f, // КПП 2 (1 чел)
            tableWidth * 0.24f, // Старший машины
            tableWidth * 0.16f  // ВГ 2
        )

        val colHeaders = arrayOf(
            "Дата",
            "КПП 1\n(1 деж. + 2 пом.)",
            "КПП 2\n(1 деж.)",
            "Старший машины\n(деж./пом.)",
            "ВГ 2\n(1 деж.)"
        )

        val headerHeight = 28f * scale
        val daysCount = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val maxVisibleDays = maxOf(daysCount, 1)
        val rowHeight = (tableBottom - tableTop - headerHeight) / maxVisibleDays

        val gridPaint = Paint().apply {
            color = Color.rgb(180, 190, 205)
            strokeWidth = 1f * scale
            style = Paint.Style.STROKE
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(235, 240, 248)
            style = Paint.Style.FILL
        }

        val weekendBgPaint = Paint().apply {
            color = Color.rgb(254, 243, 235)
            style = Paint.Style.FILL
        }

        val zebraBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }

        val changedCellBgPaint = Paint().apply {
            color = Color.rgb(254, 237, 213) // Светло-оранжевый фон для измененных назначений
            style = Paint.Style.FILL
        }

        val changedCellBorderPaint = Paint().apply {
            color = Color.rgb(234, 88, 12) // Оранжевая рамка
            strokeWidth = 1.2f * scale
            style = Paint.Style.STROKE
        }

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.5f * scale
            typeface = Typeface.DEFAULT
        }

        val cellChangedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(194, 65, 12) // Тёмно-оранжевый текст изменения
            textSize = 8.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val cellBoldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 7.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        // Отрисовка фона шапки таблицы
        canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerHeight, headerBgPaint)

        // Текст шапки колонок
        var curX = tableLeft
        for (i in colHeaders.indices) {
            val w = colWidths[i]
            val lines = colHeaders[i].split("\n")
            val midY = tableTop + headerHeight / 2f
            if (lines.size == 1) {
                canvas.drawText(lines[0], curX + w / 2f, midY + 3f * scale, headerTextPaint)
            } else {
                canvas.drawText(lines[0], curX + w / 2f, midY - 3f * scale, headerTextPaint)
                canvas.drawText(lines[1], curX + w / 2f, midY + 8f * scale, noteTextPaint.apply { textAlign = Paint.Align.CENTER })
            }
            curX += w
        }

        // Отрисовка строк по дням
        var curDate = startDate
        var rowTop = tableTop + headerHeight
        var dayIdx = 0

        fun isSlotChanged(dateStr: String, postId: String, slotIdx: Int, empId: Long?): Boolean {
            if (!highlightChanges) return false
            if (empId == null) return false
            val currentKey = "${dateStr}_${postId}_${slotIdx}_$empId"
            // Если ключ текущего назначения отсутствует в исходном снимке — назначение изменилось!
            return !baselineKeys.contains(currentKey)
        }

        while (!curDate.isAfter(endDate) && dayIdx < maxVisibleDays) {
            val dateStr = DutyRulesEngine.formatDate(curDate)
            val isWeekend = curDate.dayOfWeek.value in 6..7
            val rowBottom = rowTop + rowHeight

            // Фоновая заливка строки
            if (isWeekend) {
                canvas.drawRect(tableLeft, rowTop, tableRight, rowBottom, weekendBgPaint)
            } else if (dayIdx % 2 == 1) {
                canvas.drawRect(tableLeft, rowTop, tableRight, rowBottom, zebraBgPaint)
            }

            // Колонка 0: Дата
            val dayOfMonth = curDate.dayOfMonth
            val dayOfWeekStr = when (curDate.dayOfWeek.value) {
                1 -> "пн"; 2 -> "вт"; 3 -> "ср"; 4 -> "чт"; 5 -> "пт"; 6 -> "сб"; else -> "вс"
            }
            val dateColW = colWidths[0]
            val dateMidX = tableLeft + dateColW / 2f
            val rowMidY = rowTop + rowHeight / 2f

            canvas.drawText("$dayOfMonth", dateMidX, rowMidY - 2f * scale, cellBoldTextPaint)
            canvas.drawText(dayOfWeekStr, dateMidX, rowMidY + 9f * scale, cellBoldTextPaint.apply {
                color = if (isWeekend) Color.rgb(180, 40, 40) else Color.rgb(90, 100, 115)
            })

            // Колонка 1: КПП 1 (3 человека: слот 0, 1, 2)
            val kpp1W = colWidths[1]
            val kpp1X = tableLeft + dateColW
            val kpp1Slot0 = assignmentsMap["${dateStr}_kpp1_0"]?.firstOrNull()
            val kpp1Slot1 = assignmentsMap["${dateStr}_kpp1_1"]?.firstOrNull()
            val kpp1Slot2 = assignmentsMap["${dateStr}_kpp1_2"]?.firstOrNull()

            val kpp1Text0 = kpp1Slot0?.let { empMap[it.employeeId]?.fullName } ?: "—"
            val kpp1Text1 = kpp1Slot1?.let { empMap[it.employeeId]?.fullName } ?: "—"
            val kpp1Text2 = kpp1Slot2?.let { empMap[it.employeeId]?.fullName } ?: "—"

            val kpp1LineH = rowHeight / 3.3f
            val startY = rowTop + kpp1LineH * 0.9f

            // Подсветка КПП1
            val c0 = isSlotChanged(dateStr, "kpp1", 0, kpp1Slot0?.employeeId)
            val c1 = isSlotChanged(dateStr, "kpp1", 1, kpp1Slot1?.employeeId)
            val c2 = isSlotChanged(dateStr, "kpp1", 2, kpp1Slot2?.employeeId)

            if (c0) canvas.drawRect(kpp1X + 2f * scale, rowTop + 1f, kpp1X + kpp1W - 2f * scale, rowTop + kpp1LineH, changedCellBgPaint)
            if (c1) canvas.drawRect(kpp1X + 2f * scale, rowTop + kpp1LineH, kpp1X + kpp1W - 2f * scale, rowTop + kpp1LineH * 2, changedCellBgPaint)
            if (c2) canvas.drawRect(kpp1X + 2f * scale, rowTop + kpp1LineH * 2, kpp1X + kpp1W - 2f * scale, rowBottom - 1f, changedCellBgPaint)

            canvas.drawText(kpp1Text0 + if (c0) " •" else "", kpp1X + 6f * scale, startY, if (c0) cellChangedTextPaint else cellTextPaint)
            canvas.drawText(kpp1Text1 + if (c1) " •" else "", kpp1X + 6f * scale, startY + kpp1LineH, if (c1) cellChangedTextPaint else cellTextPaint)
            canvas.drawText(kpp1Text2 + if (c2) " •" else "", kpp1X + 6f * scale, startY + kpp1LineH * 2, if (c2) cellChangedTextPaint else cellTextPaint)

            // Колонка 2: КПП 2 (1 дежурный)
            val kpp2W = colWidths[2]
            val kpp2X = kpp1X + kpp1W
            val kpp2Slot0 = assignmentsMap["${dateStr}_kpp2_0"]?.firstOrNull()
            val kpp2Text = kpp2Slot0?.let { empMap[it.employeeId]?.fullName } ?: "—"
            val kpp2Changed = isSlotChanged(dateStr, "kpp2", 0, kpp2Slot0?.employeeId)

            if (kpp2Changed) {
                canvas.drawRect(kpp2X + 2f * scale, rowTop + 2f * scale, kpp2X + kpp2W - 2f * scale, rowBottom - 2f * scale, changedCellBgPaint)
            }
            canvas.drawText(kpp2Text + if (kpp2Changed) " •" else "", kpp2X + 6f * scale, rowMidY + 3f * scale, if (kpp2Changed) cellChangedTextPaint else cellTextPaint)

            // Колонка 3: Старший машины (Рабочий день: Утро, Обед, Вечер)
            val carW = colWidths[3]
            val carX = kpp2X + kpp2W
            val carSlot0 = assignmentsMap["${dateStr}_senior_car_0"]?.firstOrNull()
            val carSlot1 = assignmentsMap["${dateStr}_senior_car_1"]?.firstOrNull()
            val carSlot2 = assignmentsMap["${dateStr}_senior_car_2"]?.firstOrNull()

            val carEmp0 = carSlot0?.let { empMap[it.employeeId]?.fullName }
            val carEmp1 = carSlot1?.let { empMap[it.employeeId]?.fullName }
            val carEmp2 = carSlot2?.let { empMap[it.employeeId]?.fullName }

            val carCh0 = isSlotChanged(dateStr, "senior_car", 0, carSlot0?.employeeId)
            val carCh1 = isSlotChanged(dateStr, "senior_car", 1, carSlot1?.employeeId)
            val carCh2 = isSlotChanged(dateStr, "senior_car", 2, carSlot2?.employeeId)

            val allThreeSame = carEmp0 != null && carEmp0 == carEmp1 && carEmp0 == carEmp2

            if (allThreeSame) {
                if (carCh0 || carCh1 || carCh2) {
                    canvas.drawRect(carX + 2f * scale, rowTop + 2f * scale, carX + carW - 2f * scale, rowBottom - 2f * scale, changedCellBgPaint)
                }
                val mark = if (carCh0 || carCh1 || carCh2) " •" else ""
                canvas.drawText(carEmp0!! + mark, carX + 6f * scale, rowMidY - 1f * scale, if (carCh0 || carCh1 || carCh2) cellChangedTextPaint else cellTextPaint)
                canvas.drawText("У•О•В (весь день)", carX + 6f * scale, rowMidY + 9f * scale, noteTextPaint.apply { textAlign = Paint.Align.LEFT })
            } else if (carEmp0 != null || carEmp1 != null || carEmp2 != null) {
                val carLineH = rowHeight / 3.3f
                val startCarY = rowTop + carLineH * 0.9f
                val t0 = (if (carEmp0 != null) "У: $carEmp0" else "У: —") + if (carCh0) " •" else ""
                val t1 = (if (carEmp1 != null) "О: $carEmp1" else "О: —") + if (carCh1) " •" else ""
                val t2 = (if (carEmp2 != null) "В: $carEmp2" else "В: —") + if (carCh2) " •" else ""

                if (carCh0) canvas.drawRect(carX + 2f * scale, rowTop + 1f, carX + carW - 2f * scale, rowTop + carLineH, changedCellBgPaint)
                if (carCh1) canvas.drawRect(carX + 2f * scale, rowTop + carLineH, carX + carW - 2f * scale, rowTop + carLineH * 2, changedCellBgPaint)
                if (carCh2) canvas.drawRect(carX + 2f * scale, rowTop + carLineH * 2, carX + carW - 2f * scale, rowBottom - 1f, changedCellBgPaint)

                canvas.drawText(t0, carX + 6f * scale, startCarY, if (carCh0) cellChangedTextPaint else cellTextPaint)
                canvas.drawText(t1, carX + 6f * scale, startCarY + carLineH, if (carCh1) cellChangedTextPaint else cellTextPaint)
                canvas.drawText(t2, carX + 6f * scale, startCarY + carLineH * 2, if (carCh2) cellChangedTextPaint else cellTextPaint)
            } else {
                canvas.drawText("—", carX + 6f * scale, rowMidY + 3f * scale, cellTextPaint)
            }

            // Колонка 4: ВГ 2
            val vg2W = colWidths[4]
            val vg2X = carX + carW
            val vg2Slot0 = assignmentsMap["${dateStr}_vg2_0"]?.firstOrNull()
            val vg2Text = vg2Slot0?.let { empMap[it.employeeId]?.fullName } ?: "—"
            val vg2Changed = isSlotChanged(dateStr, "vg2", 0, vg2Slot0?.employeeId)

            if (vg2Changed) {
                canvas.drawRect(vg2X + 2f * scale, rowTop + 2f * scale, vg2X + vg2W - 2f * scale, rowBottom - 2f * scale, changedCellBgPaint)
            }
            canvas.drawText(vg2Text + if (vg2Changed) " •" else "", vg2X + 6f * scale, rowMidY + 3f * scale, if (vg2Changed) cellChangedTextPaint else cellTextPaint)

            // Горизонтальная линия под строкой
            canvas.drawLine(tableLeft, rowBottom, tableRight, rowBottom, gridPaint)

            rowTop = rowBottom
            curDate = curDate.plusDays(1)
            dayIdx++
        }

        // Вертикальные линии сетки
        canvas.drawRect(tableLeft, tableTop, tableRight, tableBottom, gridPaint)
        var vertX = tableLeft
        for (w in colWidths) {
            vertX += w
            if (vertX < tableRight) {
                canvas.drawLine(vertX, tableTop, vertX, tableBottom, gridPaint)
            }
        }

        // Подвал: подписи ответственных
        val footerY = height - 12f * scale
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 70, 85)
            textSize = 8.5f * scale
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Составил: ___________________________", tableLeft + 10f * scale, footerY, footerPaint)
        canvas.drawText("Утверждаю: ___________________________", tableRight - 180f * scale, footerY, footerPaint)
    }

    /**
     * Сохранение (скачивание) файла в общедоступную папку Загрузки (Downloads) устройства
     */
    fun saveFileToDownloads(context: Context, sourceFile: File, mimeType: String): Boolean {
        return try {
            val fileName = sourceFile.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val collectionUri = if (mimeType.startsWith("image/")) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                val uri = resolver.insert(collectionUri, contentValues) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Зарегистрировать в системе для уведомления
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                downloadManager?.addCompletedDownload(
                    fileName,
                    "График дежурств",
                    true,
                    mimeType,
                    destFile.absolutePath,
                    destFile.length(),
                    true
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Открывает системное меню "Поделиться" для файла
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
