package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financial.Money
import com.example.navigation.MekkaDestination
import com.example.repository.SystemAlertItem
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.MekkaGoldPrimary
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Represents an in-app operational notification recorded during user actions.
 */
data class InAppNotificationEntry(
    val id: String,
    val titleArabic: String,
    val categoryArabic: String,
    val isError: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
    val routeTarget: String? = null
)

/**
 * Structured explanatory guide metadata for each station screen.
 */
data class StationGuidanceSpec(
    val destination: MekkaDestination,
    val bannerTitleArabic: String,
    val summaryArabic: String,
    val accountingRuleArabic: String,
    val securityAndUxTipArabic: String
)

object StationGuidanceCatalog {
    fun forDestination(destination: MekkaDestination): StationGuidanceSpec = when (destination) {
        MekkaDestination.CONTROL_CENTER -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "دليل مركز التحكم المالي والتشغيلي الحي",
            summaryArabic = "يعرض الأرصدة المشتقة لحظياً من دفتر الأستاذ غير القابل للتعديل دون أي أرقام مخزنة يدوياً أو تقديرية.",
            accountingRuleArabic = "القاعدة المحاسبية: إجمالي المدين (Debits) == إجمالي الدائن (Credits) عبر كافة حسابات الشجرة المحاسبية (1000 إلى 7000).",
            securityAndUxTipArabic = "نصيحة سريعة: اضغط على أي بطاقة مؤشر (KPI) أو أي تنبيه حي للانتقال المباشر للوحدة المختصة، أو استخدم شريط البحث الشامل للوصول الفوري لأي سيريال IMEI أو قيد."
        )
        MekkaDestination.CUSTOMERS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: إدارة العملاء، الذمم، وتحصيل الدفعات",
            summaryArabic = "يتيح تسجيل العملاء ومتابعة كشف الحساب المشتق (إجمالي المبيعات الآجلة مطروحاً منه الدفعات المسددة والمرتجعات).",
            accountingRuleArabic = "القيد المزدوج عند التحصيل: من حـ/ الخزينة النقدية (1000 DR) إلى حـ/ ذمم العملاء المدينين (1100 CR).",
            securityAndUxTipArabic = "حماية البيانات: أرشفة العميل (Soft Delete) تخفيه من القوائم النشطة مع الاحتفاظ الكامل بتاريخ فواتيره وصيانته وقيوده المحاسبية."
        )
        MekkaDestination.SUPPLIERS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: الموردين وسداد المستحقات الآجلة",
            summaryArabic = "يتابع فواتير التوريد والمديونيات المستحقة للموردين بدقة قرشية مع إمكانية السداد النقدي الكلي أو الجزئي.",
            accountingRuleArabic = "القيد المزدوج عند سداد المورد: من حـ/ ذمم الموردين الدائنين (2000 DR) إلى حـ/ الخزينة النقدية (1000 CR).",
            securityAndUxTipArabic = "ضابط مالي: لا يمكن سداد دفعة لمورد تتجاوز الرصيد النقدي الفعلي المتاح في الخزينة النشطة."
        )
        MekkaDestination.PRODUCTS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: المخزون القائم على الحركات والمتوسط المرجح (WAC)",
            summaryArabic = "الأرصدة المخزنية لا تُعدل يدوياً أبداً؛ بل تُشتق حصرياً من حركات التوريد والبيع والتسوية وصرف قطع الصيانة.",
            accountingRuleArabic = "التقييم المحاسبي: يُعاد احتساب المتوسط المرجح للتكلفة (WAC) تلقائياً مع كل فاتورة شراء أو حركة تسوية واردة.",
            securityAndUxTipArabic = "تتبع الأجهزة: فعّل خيار (يتبع سيريال / IMEI) للهواتف لمنع تكرار السيريال النشط وتتبع دورة حياة الجهاز بالكامل."
        )
        MekkaDestination.SALES -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: نقطة البيع الذرية (POS) والمرتجعات المحاسبية",
            summaryArabic = "تدعم البيع المفرد أو سلة الأصناف المتعددة (نقدي بالكامل، دفعة مقدمة + آجل، أو بيع آجل بالكامل) مع معاينة ومشاركة الإيصال.",
            accountingRuleArabic = "القيد المزدوج للبيع: إثبات الإيراد (حـ/ الخزينة أو ذمم العملاء DR إلى حـ/ المبيعات 4000 CR) + إثبات التكلفة (حـ/ تكلفة البضاعة المباعة 5000 DR إلى حـ/ المخزون 1200 CR).",
            securityAndUxTipArabic = "المرتجعات: استرجاع الفاتورة لا يحذف البيع الأصلي، بل يُصدر قيد REFUND عكسي ويعيد الكمية والسيريال للمخزن ذرياً."
        )
        MekkaDestination.PURCHASES -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: فواتير المشتريات والتوريد المخزني الذري",
            summaryArabic = "تُدخل البضاعة للمخزن وتُسجل أرقام السيريال/IMEI الجديدة وتُحدث المتوسط المرجح للتكلفة في معاملة ذرية واحدة.",
            accountingRuleArabic = "القيد المزدوج للشراء: من حـ/ المخزون (1200 DR) إلى مذكورين: حـ/ الخزينة النقدية (1000 CR بالمدفوع) وحـ/ الموردين (2000 CR بالمتبقي الآجل).",
            securityAndUxTipArabic = "تنبيه تشغيلي: اترك حقل (المبلغ المسدد نقداً الآن) فارغاً للسداد النقدي الكامل، أو أدخل 0 للشراء الآجل بالكامل على حساب المورد."
        )
        MekkaDestination.MAINTENANCE -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: مركز الصيانة، العربون، وصرف قطع الغيار",
            summaryArabic = "يدير دورة حياة تذكرة الصيانة (استلام ← فحص ← انتظار قطعة ← جاهز للتسليم ← تم التسليم والتحصيل) مع ربط العربون وقطع الغيار.",
            accountingRuleArabic = "الأثر المالي والمخزني: صرف قطعة غيار يخصمها بحركة MAINTENANCE_USAGE، وعند التسليم يُرحل صافي التحصيل في دفتر الأستاذ.",
            securityAndUxTipArabic = "تنبيه ذكي: الأجهزة الجاهزة للتسليم تظهر تلقائياً في شريط التنبيهات الحية بمركز التحكم لتسريع التواصل مع العميل."
        )
        MekkaDestination.WALLETS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: المحافظ الإلكترونية الخمس والحدود اليومية/الشهرية",
            summaryArabic = "يدعم فودافون كاش، أورنج كاش، اتصالات كاش، إنستا باي، وأكسس باي مع حساب الرصيد والحدود الرقابية لحظياً من القيود.",
            accountingRuleArabic = "القيد المزدوج للمحافظ: يعالج أصل المبلغ بين حساب المحفظة (1010–1050) والخزينة (1000) مع فصل رسوم الشبكة (7000 DR) وعمولة المحل.",
            securityAndUxTipArabic = "حماية السقف: يمنع النظام تلقائياً أي تحويل يتجاوز الرصيد المتاح أو السقف اليومي/الشهري المعتمد للمحفظة."
        )
        MekkaDestination.SMS_INTELLIGENCE -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: محرك ذكاء رسائل SMS المالية والبوابات الخمس",
            summaryArabic = "يلتقط ويحلل رسائل المحافظ الخمس تلقائياً لاستخراج المزود، نوع العملية، المبلغ، الرسوم، الرقم المرجعي، ودرجة الثقة.",
            accountingRuleArabic = "ضابط الاعتماد الذري: الرسالة تُعامل كدليل مساعد (Supporting Evidence) ولا تؤثر مالياً إلا بعد اجتياز 5 بوابات تحقق أو الاعتماد اليدوي.",
            securityAndUxTipArabic = "منع التكرار (Idempotency): يحمي النظام دفتر الأستاذ من تكرار نفس الرسالة عبر بصمة SHA-256 والرقم المرجعي للعملية."
        )
        MekkaDestination.CASH_DRAWER -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: الخزينة النقدية ومطابقة تقفيل الوردية",
            summaryArabic = "يتابع السيولة النقدية الفعلية بالدرج وحركات التوريد (CASH_IN) والصرف (CASH_OUT) وجلسات تقفيل الوردية.",
            accountingRuleArabic = "القيد المزدوج للخزينة: كل حركة نقدية تقترن بحساب مقابل متوازن، وعند التقفيل يُقارن الرصيد الدفتري بالنقدية الفعلية المعدودة.",
            securityAndUxTipArabic = "رقابة الورديات: أي عجز أو فائض عند الجرد الفعلي يُوثق رسمياً في سجل جلسات الخزينة وسجل التدقيق الأمني."
        )
        MekkaDestination.EXPENSES -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: المصروفات التشغيلية للمتجر",
            summaryArabic = "يسجل مصروفات الإيجار، الكهرباء، الإنترنت، الرواتب، والنثريات مع خصمها الفوري من الخزينة وإدراجها في تقرير صافي الربح.",
            accountingRuleArabic = "القيد المزدوج للمصروف: من حـ/ المصروفات التشغيلية (6000 DR) إلى حـ/ الخزينة النقدية (1000 CR).",
            securityAndUxTipArabic = "ضابط السيولة: يتحقق النظام من كفاية النقدية بالخزينة قبل السماح بترحيل أي مصروف تشغيلي."
        )
        MekkaDestination.OPERATIONS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: دفتر الأستاذ غير القابل للتعديل، التدقيق، والمزامنة",
            summaryArabic = "يعرض كافة القيود المالية وسجل التدقيق وطابور المزامنة السحابية الموثقة وتعارضات البيانات التي تتطلب الحسم.",
            accountingRuleArabic = "عدم القابلية للتعديل (Immutability): يُمنع حذف أو تعديل أي قيد مالي معتمد؛ التصحيح يتم حصرياً عبر إصدار قيد عكسي (REVERSAL).",
            securityAndUxTipArabic = "المزامنة الآمنة: لا تتحول حالة السجل إلى SYNCED إلا بعد استلام إقرار سحابي موثق (ACK) مع حماية Idempotency."
        )
        MekkaDestination.REPORTS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: التقارير المالية والتشغيلية وتصدير البيانات",
            summaryArabic = "يلخص صافي الربح، مجمل الربح، تكلفة البضاعة المباعة (COGS)، المصروفات، وتقييم المخزون الحالي بدقة محاسبية.",
            accountingRuleArabic = "معادلة الربحية: مجمل الربح = صافي المبيعات − تكلفة البضاعة المباعة (WAC) • صافي الربح = مجمل الربح + عمولات المحافظ − المصروفات.",
            securityAndUxTipArabic = "تصدير ومشاركة: يمكنك نسخ أو مشاركة التقرير المالي الكامل بصيغة نصية أو CSV للمراجعة المحاسبية الخارجية."
        )
        MekkaDestination.SETTINGS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: الأمان، الصلاحيات (RBAC)، النسخ الاحتياطي، وفحص السلامة",
            summaryArabic = "يتحكم في هوية المتجر، تبديل المستخدم النشط، النسخ الاحتياطي المشفر، الاستعادة الآمنة من 10 خطوات، وفحص 16 معيار سلامة.",
            accountingRuleArabic = "فحص التوازن الشامل: يتحقق فحص السلامة (16 فحصاً) من توازن القيد المزدوج، تطابق الأرصدة المشتقة، وسلامة مفاتيح الربط.",
            securityAndUxTipArabic = "الاستعادة الآمنة: قبل أي استعادة لنسخة احتياطية، ينشئ النظام تلقائياً نسخة أمان للبيانات الحالية لمنع فقدان أي عملية."
        )
        MekkaDestination.MORE -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "دليل قائمة المزيد والعمليات الإدارية",
            summaryArabic = "تجمع العمليات اليومية، تقارير الإدارة، وأدوات النظام في تبويب منظم وواضح.",
            accountingRuleArabic = "جميع العمليات في الأقسام الفرعية مرتبطة مباشرة بدفتر الأستاذ المالي وحركات المخزن.",
            securityAndUxTipArabic = "تم فصل أدوات التشخيص والمستخدمين في قسم النظام لحماية سير العمل اليومي للكاشير."
        )
        MekkaDestination.LEDGER, MekkaDestination.AUDIT, MekkaDestination.SYNC -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: دفتر الأستاذ، التدقيق، والمزامنة",
            summaryArabic = "يعرض القيود المالية غير القابلة للتعديل وسجل الرقابة وحالة المزامنة السحابية.",
            accountingRuleArabic = "يُمنع حذف أو تعديل أي قيد مرحل؛ يتم التصحيح حصرياً عبر قيد عكسي (REVERSAL).",
            securityAndUxTipArabic = "كل عملية مالية أو مخزنية توثق تلقائياً بهوية المستخدم والوقت."
        )
        MekkaDestination.BACKUP -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: النسخ الاحتياطي المشفر والاستعادة الآمنة",
            summaryArabic = "حفظ واستعادة بيانات المتجر مع التحقق من البصمة الرقمية SHA-256.",
            accountingRuleArabic = "يتم التحقق من سلامة القيود والأرصدة قبل وبعد أي عملية استعادة.",
            securityAndUxTipArabic = "ينشئ النظام نسخة أمان تلقائية قبل استعادة أي ملف خارجي."
        )
        MekkaDestination.USERS_PERMISSIONS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: المستخدمون والصلاحيات (RBAC)",
            summaryArabic = "إدارة الموظفين وتحديد صلاحيات كل دور وظيفي في المتجر.",
            accountingRuleArabic = "يرتبط كل قيد محاسبي وفاتورة بمعرف المستخدم المنفذ للعملية.",
            securityAndUxTipArabic = "تبديل المستخدم النشط للاختبار متاح في شاشة Diagnostics فقط."
        )
        MekkaDestination.DIAGNOSTICS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: فحص سلامة النظام وأدوات التشخيص (Diagnostics)",
            summaryArabic = "فحص 16 معياراً محاسبياً ومخزنياً وأدوات الاختبار المعزولة عن بيئة الكاشير.",
            accountingRuleArabic = "يتحقق الفحص من تطابق المدين والدائن وعدم وجود أرصدة سالبة أو سيريالات مكررة.",
            securityAndUxTipArabic = "هذه الشاشة مخصصة لمدير النظام والتشخيص الفني فقط."
        )
        MekkaDestination.PENDING_OPERATIONS -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: مركز العمليات المعلقة والمتابعة الموحد",
            summaryArabic = "يجمع عمليات المزامنة المعلقة والفاشلة، تعارضات البيانات، مراجعات SMS، الصيانة، وتنبيهات المخزون في شاشة واحدة.",
            accountingRuleArabic = "ضابط الرقابة: لا يتم تجاهل أي عملية معلقة أو فاشلة، بل تُعرض مع السبب والإجراء المطلوب (Review / Retry / Resolve / Approve).",
            securityAndUxTipArabic = "اضغط على أي إجراء للتنفيذ المباشر أو فتح سجل العملية الأصلي."
        )
        MekkaDestination.SAFE_AUTOMATION -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: محرك الأتمتة والرقابة الآمنة",
            summaryArabic = "إدارة وتفعيل قواعد التنبيه الآلية للمخزون والمحافظ والنسخ الاحتياطي مع ضمان الأمان التشغيلي التام.",
            accountingRuleArabic = "قاعدة السلامة: لا توجد أي معاملات مالية تلقائية إطلاقاً دون اعتماد وموافقة بشرية.",
            securityAndUxTipArabic = "يمكن تفعيل أو تعطيل أي قاعدة أتمتة عبر مفتاح التبديل المباشر."
        )
        MekkaDestination.DATA_INTEGRITY -> StationGuidanceSpec(
            destination = destination,
            bannerTitleArabic = "شريط توضيح: مركز سلامة وصيانة البيانات",
            summaryArabic = "الفحص الشامل لقيود دفتر الأستاذ، حركات المخزون، جلسات الخزينة، وطابور المزامنة.",
            accountingRuleArabic = "ضابط الاتساق: رصد فوري لأي خلل أو تعارض وتوفير مسار إصلاح معتمد وموثق بالدفاتر.",
            securityAndUxTipArabic = "مقترحات الإصلاح تتطلب اعتماداً إشرافياً وتُسجل مباشرة في سجل التدقيق."
        )
    }
}


/**
 * Reusable Financial KPI Card for Control Center & Reports — optimized for Daylight-First Light Mode.
 */
@Composable
fun FinancialKpiCard(
    title: String,
    formattedValue: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTagName: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName)
            .then(
                if (onClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = LedgerMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Reusable Empty State Card (Section 20: Icon + Clear Explanation + Next Action Button).
 * Never shows fake data; always guides the user on what to do next.
 */
@Composable
fun MekkaEmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Info,
    testTagName: String = "empty_state_card",
    modifier: Modifier = Modifier,
    actionLabelArabic: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!actionLabelArabic.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.testTag("${testTagName}_action_btn")
                ) {
                    Text(actionLabelArabic, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Pop-up Feedback Toast & Banner for operation results (atomic transaction confirmation or validation error).
 * Preserves immediate synchronous rendering for Robolectric tests while auto-dismissing after 5.5 seconds.
 * Enforces Section 7 (Real State Persistence Note), Section 8 (Error Sanitization), and Section 9 (Actionable Errors).
 */
@Composable
fun OperationFeedbackBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 5500L,
    actionLabelArabic: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    if (message.isNullOrBlank()) return
    LaunchedEffect(message, isError) {
        if (autoDismissMillis > 0L && !isError) {
            delay(autoDismissMillis)
            onDismiss()
        }
    }
    val bannerColor = if (isError) FinancialNegative else FinancialPositive
    val badgeLabel = if (isError) "تنبيه تحقق وتدقيق !" else "تم التنفيذ الذري ✓"
    val sanitizedMessage = if (isError) {
        UxErrorSanitizer.sanitizeUserMessage(message)
    } else {
        message
    }
    val persistenceNote = if (isError) {
        if (!sanitizedMessage.contains("لم يتم تغيير البيانات")) "لم يتم تغيير البيانات. حاول مرة أخرى." else ""
    } else {
        if (!sanitizedMessage.contains("تم حفظ العملية") && !sanitizedMessage.contains("محفوظ")) {
            "تم حفظ العملية على الجهاز وسيتم مزامنتها عند توفر الاتصال"
        } else ""
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(if (isError) "error_feedback_banner" else "success_feedback_banner")
            .clickable { onDismiss() },
        shape = RoundedCornerShape(14.dp),
        color = bannerColor.copy(alpha = 0.12f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.2.dp, bannerColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bannerColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    contentDescription = if (isError) "تنبيه خطأ" else "نجاح العملية",
                    tint = bannerColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPillBadge(
                        text = badgeLabel,
                        color = bannerColor
                    )
                    Text(
                        text = "إشعار فوري • يُغلق تلقائياً أو بالضغط",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = sanitizedMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (persistenceNote.isNotBlank()) {
                    Text(
                        text = persistenceNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!actionLabelArabic.isNullOrBlank() && onActionClick != null) {
                OutlinedButton(
                    onClick = onActionClick,
                    border = BorderStroke(1.dp, bannerColor),
                    modifier = Modifier.testTag("feedback_banner_action_button")
                ) {
                    Text(
                        text = actionLabelArabic,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = bannerColor
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق التنبيه",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Contextual Explanatory & Accounting Guidance Banner displayed at the top of each station screen.
 * Explains operational workflow, Double-Entry Accounting (DR/CR), and security/audit guarantees.
 */
@Composable
fun ContextualGuidanceBanner(
    destination: MekkaDestination,
    visible: Boolean,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val spec = StationGuidanceCatalog.forDestination(destination)
    var isExpanded by rememberSaveable(destination.route) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("contextual_guidance_banner_${destination.route}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "إرشاد توضيحي",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.bannerTitleArabic,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = spec.summaryArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .testTag("toggle_guidance_details_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "طي الشرح" else "القيد والضوابط",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "تبديل التفاصيل",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("dismiss_guidance_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إخفاء شريط التوضيح",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MekkaGoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = spec.accountingRuleArabic,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = LedgerMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = FinancialPositive,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = spec.securityAndUxTipArabic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pop-up Notification & Live Alert Center Dialog accessible from the TopAppBar bell icon.
 */
@Composable
fun NotificationCenterDialog(
    systemAlerts: List<SystemAlertItem>,
    notificationHistory: List<InAppNotificationEntry>,
    showGuidanceBanners: Boolean,
    onToggleGuidanceBanners: () -> Unit,
    onClearHistory: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onDismiss: () -> Unit,
    smartNotificationBundle: com.example.intelligence.SmartNotificationCenterBundle = com.example.intelligence.SmartNotificationCenterBundle(),
    onOpenSmartAlertSource: (routeTarget: String, entityId: String, reference: String) -> Unit = { route, _, _ ->
        onNavigateToRoute(route)
        onDismiss()
    }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notification_center_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "مركز الإشعارات والتنبيهات الذكي",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                StatusPillBadge(
                    text = "${systemAlerts.size} تنبيه حي",
                    color = if (systemAlerts.isEmpty()) FinancialPositive else FinancialWarning
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "أشرطة التوضيح والإرشاد المحاسبي",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (showGuidanceBanners) "مفعلة أعلى جميع الشاشات لتوضيح القيود والضوابط" else "مخفية حالياً — اضغط لإظهارها",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onToggleGuidanceBanners,
                                modifier = Modifier.testTag("dialog_toggle_guidance_button")
                            ) {
                                Text(if (showGuidanceBanners) "إخفاء الأشرطة" else "إظهار الأشرطة")
                            }
                        }
                    }
                }

                item {
                    com.example.ui.screens.SmartNotificationCenterSection(
                        bundle = smartNotificationBundle,
                        onOpenSource = { route, entityId, reference ->
                            onOpenSmartAlertSource(route, entityId.orEmpty(), reference)
                            onDismiss()
                        }
                    )
                }

                item {
                    Text(
                        text = "1. التنبيهات التشغيلية والمالية الحية (${systemAlerts.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (systemAlerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = FinancialPositive.copy(alpha = 0.10f)),
                            border = BorderStroke(1.dp, FinancialPositive.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = FinancialPositive
                                )
                                Text(
                                    text = "جميع المؤشرات المالية والمخزنية وطابور المزامنة في حالة مثالية (لا توجد تنبيهات معلقة).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    items(systemAlerts, key = { "modal_alert_${it.code}" }) { alert ->
                        val alertColor = when (alert.severity) {
                            "CRITICAL" -> FinancialNegative
                            "WARNING" -> FinancialWarning
                            else -> FinancialInfo
                        }
                        val severityArabic = when (alert.severity) {
                            "CRITICAL" -> "حرج"
                            "WARNING" -> "تحذير"
                            else -> "معلومة"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToRoute(alert.routeTarget)
                                    onDismiss()
                                }
                                .testTag("modal_alert_card_${alert.code}"),
                            colors = CardDefaults.cardColors(containerColor = alertColor.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, alertColor.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPillBadge(text = severityArabic, color = alertColor)
                                    Text(
                                        text = "انتقال فوري للحل ←",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = alertColor
                                    )
                                }
                                Text(
                                    text = alert.titleArabic,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. سجل إشعارات العمليات الأخيرة (${notificationHistory.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (notificationHistory.isNotEmpty()) {
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier.testTag("clear_notification_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(" مسح السجل", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (notificationHistory.isEmpty()) {
                    item {
                        Text(
                            text = "لم يتم تسجيل إشعارات عمليات في هذه الجلسة بعد. ستظهر هنا كافة عمليات الحفظ والترحيل والتنبيهات مع توقيتها.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(notificationHistory, key = { it.id }) { entry ->
                        val entryColor = if (entry.isError) FinancialNegative else FinancialPositive
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, entryColor.copy(alpha = 0.38f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPillBadge(
                                        text = entry.categoryArabic,
                                        color = entryColor
                                    )
                                    Text(
                                        text = formatTimestampArabic(entry.timestampMillis),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = entry.titleArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_notification_center_button")
            ) {
                Text("إغلاق مركز الإشعارات")
            }
        }
    )
}

@Composable
fun SectionHeaderRow(
    title: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector = inferSemanticIconForBadge(text, color)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
    }
}

@Composable
fun MekkaStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector = inferSemanticIconForBadge(text, color)
) {
    StatusPillBadge(text = text, color = color, modifier = modifier, icon = icon)
}

@Composable
fun MekkaSectionHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatMoneyMinor(amountMinor: Long): String =
    Money.ofMinor(amountMinor).formatArabic()

fun formatTimestampArabic(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return sdf.format(Date(timestampMillis))
}

/**
 * Cryptographic SHA-256 Accounting Verification Stamp Card for Invoices, Maintenance Tickets, and Product SKU Labels.
 * Never renders a fake QR code (Rule 22).
 */
@Composable
fun QrVerificationBadgeCard(
    titleArabic: String,
    referenceCode: String,
    payloadText: String,
    modifier: Modifier = Modifier
) {
    val digestBytes = MessageDigest.getInstance("SHA-256")
        .digest("$referenceCode|$payloadText".toByteArray(Charsets.UTF_8))
    val shortHash = digestBytes.take(6).joinToString("") { "%02X".format(it) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("qr_verification_card_$referenceCode"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = titleArabic,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "المرجع المحاسبي: $referenceCode • SHA-256: #$shortHash",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = payloadText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Native Android Voice-to-Text Dictation Button (Arabic speech recognition without external paid APIs).
 */
@Composable
fun VoiceDictationIconButton(
    promptArabic: String = "تحدث الآن للإدخال الصوتي السريع...",
    onResultText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty()) {
                onResultText(spoken)
            }
        }
    }

    IconButton(
        onClick = {
            runCatching {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, promptArabic)
                }
                speechLauncher.launch(intent)
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "إملاء صوتي بالعربية",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Normalizes an Egyptian phone number and opens WhatsApp (or system share chooser fallback) with a prefilled Arabic message.
 */
fun launchWhatsAppMessage(context: Context, rawPhone: String, messageArabic: String) {
    val digits = Money.normalizeDigits(rawPhone).filter { it.isDigit() }
    val intlPhone = when {
        digits.startsWith("20") -> digits
        digits.startsWith("01") -> "2$digits"
        digits.isNotEmpty() -> "20$digits"
        else -> ""
    }
    runCatching {
        val encodedText = Uri.encode(messageArabic)
        val uri = if (intlPhone.isNotEmpty()) {
            Uri.parse("https://wa.me/$intlPhone?text=$encodedText")
        } else {
            Uri.parse("https://wa.me/?text=$encodedText")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageArabic)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallback, "إرسال عبر واتساب أو تطبيق مراسلة"))
        }
    }
}

/**
 * Opens the native phone dialer prefilled with the customer/supplier phone number.
 */
fun launchPhoneDialer(context: Context, rawPhone: String) {
    val cleaned = Money.normalizeDigits(rawPhone).trim()
    if (cleaned.isEmpty()) return
    runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Visual color-coded progress bar for Daily & Monthly Wallet Limit utilization.
 * Transitions smoothly from Emerald (< 60%) to Warning Gold (60%–79%) to Danger Red (>= 80%).
 */
@Composable
fun WalletLimitProgressBar(
    labelArabic: String,
    usedMinor: Long,
    limitMinor: Long,
    modifier: Modifier = Modifier
) {
    val ratio = if (limitMinor > 0L) {
        (usedMinor.toFloat() / limitMinor.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val pct = if (limitMinor > 0L) ((usedMinor * 100L) / limitMinor).coerceIn(0L, 999L) else 0L
    val barColor = when {
        pct >= 80L -> FinancialNegative
        pct >= 60L -> FinancialWarning
        else -> FinancialPositive
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$labelArabic: ${formatMoneyMinor(usedMinor)} / ${formatMoneyMinor(limitMinor)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$pct% (المتبقي: ${formatMoneyMinor((limitMinor - usedMinor).coerceAtLeast(0L))})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = LedgerMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.16f)
        )
    }
}

/**
 * Interactive 58mm / 80mm POS Thermal & PDF Receipt Preview Dialog (Rule 22).
 * Supports Preview, Thermal 58mm, Thermal 80mm, PDF / Print, Share, and WhatsApp.
 */
@Composable
fun ThermalReceiptPreviewDialog(
    shopNameArabic: String,
    shopPhone: String,
    documentTitleArabic: String,
    referenceNumber: String,
    customerName: String,
    customerPhone: String,
    detailsLines: List<Pair<String, String>>,
    totalMinor: Long,
    paidMinor: Long,
    remainingMinor: Long,
    footerNoteArabic: String,
    onDismiss: () -> Unit,
    context: Context
) {
    var selectedPaperWidthMm by remember { mutableStateOf(80) } // 58 or 80

    val formattedReceiptText = buildString {
        appendLine("🧾 *$documentTitleArabic — $shopNameArabic*")
        if (shopPhone.isNotBlank()) appendLine("هاتف المتجر: $shopPhone")
        appendLine("المرجع: $referenceNumber")
        appendLine("العميل: $customerName ${if (customerPhone.isNotBlank() && customerPhone != "-") "($customerPhone)" else ""}")
        appendLine(if (selectedPaperWidthMm == 58) "------------------------" else "--------------------------------")
        detailsLines.forEach { (k, v) -> appendLine("• $k: $v") }
        appendLine(if (selectedPaperWidthMm == 58) "------------------------" else "--------------------------------")
        appendLine("الإجمالي: ${formatMoneyMinor(totalMinor)}")
        appendLine("المدفوع: ${formatMoneyMinor(paidMinor)}")
        appendLine("المتبقي: ${formatMoneyMinor(remainingMinor)}")
        if (footerNoteArabic.isNotBlank()) appendLine(footerNoteArabic)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "معاينة الفاتورة (${selectedPaperWidthMm}mm / PDF)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Thermal 58mm vs 80mm selector (Rule 22)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedPaperWidthMm = 58 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receipt_width_58mm_btn"),
                        colors = if (selectedPaperWidthMm == 58) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("حراري 58mm", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { selectedPaperWidthMm = 80 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receipt_width_80mm_btn"),
                        colors = if (selectedPaperWidthMm == 80) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("حراري 80mm / PDF", fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("thermal_receipt_preview_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (selectedPaperWidthMm == 58) 10.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = shopNameArabic,
                            style = if (selectedPaperWidthMm == 58) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            } else {
                                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            },
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (shopPhone.isNotBlank()) {
                            Text(
                                text = "خدمة العملاء: $shopPhone",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = LedgerMonoFontFamily)
                            )
                        }
                        StatusPillBadge(text = "$documentTitleArabic (${selectedPaperWidthMm}mm)", color = MekkaGoldPrimary)
                        Text(
                            text = "رقم المرجع: $referenceNumber",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = LedgerMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "العميل: $customerName ${if (customerPhone.isNotBlank() && customerPhone != "-") "($customerPhone)" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        detailsLines.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = LedgerMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الإجمالي المعتمد:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = formatMoneyMinor(totalMinor),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المدفوع:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatMoneyMinor(paidMinor),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LedgerMonoFontFamily),
                                color = FinancialPositive
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = formatMoneyMinor(remainingMinor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (remainingMinor > 0L) FinancialWarning else FinancialPositive
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        QrVerificationBadgeCard(
                            titleArabic = "ختم التحقق المحاسبي (SHA-256)",
                            referenceCode = referenceNumber,
                            payloadText = "$shopNameArabic|$referenceNumber|TOTAL:${formatMoneyMinor(totalMinor)}"
                        )
                        if (footerNoteArabic.isNotBlank()) {
                            Text(
                                text = footerNoteArabic,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action Row: طباعة / PDF + مشاركة + WhatsApp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.example.diagnostics.ReceiptFormatter.printInvoiceOrReceipt(
                                context = context,
                                jobName = "MekkaPhone_$referenceNumber",
                                shopNameArabic = shopNameArabic,
                                documentTitleArabic = "$documentTitleArabic (${selectedPaperWidthMm}mm)",
                                referenceNumber = referenceNumber,
                                detailsMap = detailsLines + listOf(
                                    "العميل" to customerName,
                                    "الإجمالي" to formatMoneyMinor(totalMinor),
                                    "المدفوع" to formatMoneyMinor(paidMinor),
                                    "المتبقي" to formatMoneyMinor(remainingMinor)
                                ),
                                footerNoteArabic = footerNoteArabic
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("thermal_receipt_print_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة / PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            com.example.diagnostics.ReceiptFormatter.sharePlainTextReceipt(
                                context = context,
                                title = "$documentTitleArabic #$referenceNumber",
                                body = formattedReceiptText
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("thermal_receipt_share_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    launchWhatsAppMessage(context, customerPhone, formattedReceiptText)
                },
                modifier = Modifier.testTag("thermal_receipt_whatsapp_btn")
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                Text(" إرسال عبر واتساب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق المعاينة")
            }
        }
    )
}

data class QuickActionShortcutItem(
    val titleArabic: String,
    val subtitleArabic: String,
    val badgeArabic: String,
    val routeTarget: String,
    val accentColor: Color
)

/**
 * 1-Tap Quick Action Speed-Dial Modal Dialog for rapid daily store operations.
 */
@Composable
fun QuickActionLauncherDialog(
    shortcuts: List<QuickActionShortcutItem>,
    onSelectRoute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("quick_action_launcher_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MekkaGoldPrimary
                    )
                    Text(
                        text = "مشغل العمليات السريعة (1-Tap POS)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shortcuts, key = { it.routeTarget }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectRoute(item.routeTarget)
                                onDismiss()
                            }
                            .testTag("quick_action_item_${item.routeTarget}"),
                        colors = CardDefaults.cardColors(containerColor = item.accentColor.copy(alpha = 0.10f)),
                        border = BorderStroke(1.dp, item.accentColor.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.titleArabic,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitleArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPillBadge(text = item.badgeArabic, color = item.accentColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * Barcode / IMEI Scanner & Fast Entry Dialog for POS and Inventory workflows (Rule 14 & 15).
 */
@Composable
fun BarcodeCameraScannerDialog(
    titleArabic: String,
    onBarcodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var manualCode by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("barcode_scanner_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = titleArabic,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "امسح الباركود / السيريال عبر قارئ الباركود المتصل أو الكاميرا، أو أدخل الكود مباشرة:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("باركود / SKU / IMEI") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("barcode_scanner_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualCode.isNotBlank()) {
                        onBarcodeDetected(manualCode.trim())
                    }
                },
                enabled = manualCode.isNotBlank(),
                modifier = Modifier.testTag("barcode_scanner_confirm_btn")
            ) {
                Text("تأكيد الكود")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Collapsible container for technical details (UUIDs, SHA-256 hashes, raw exceptions, JSON snapshots)
 * so everyday screens remain friendly while preserving full diagnostic transparency (Part 3 Rules 4, 6, 16).
 */
@Composable
fun ExpandableTechnicalDetails(
    titleArabic: String = "التفاصيل الفنية والتشخيصية",
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleArabic,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = titleArabic,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content
                )
            }
        }
    }
}


