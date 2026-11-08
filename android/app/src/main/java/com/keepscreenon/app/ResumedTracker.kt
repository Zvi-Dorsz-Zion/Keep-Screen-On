package com.keepscreenon.app

/**
 * מחשב אילו אפליקציות פתוחות על המסך, מתוך זרם האירועים של UsageStatsManager.
 * קוטלין טהור, כדי שאפשר יהיה לבדוק אותו בלי מכשיר.
 *
 * עוקבים אחרי כל הפעילויות במצב "פעיל" ולא רק אחרי האחרונה, כי במסך מפוצל שתי אפליקציות פעילות יחד.
 * קריאה חוזרת של אותו אירוע לא משנה את התוצאה, ולכן אפשר לקרוא שוב מחותמת הזמן האחרונה בלי לפספס.
 */
class ResumedTracker {
    /** אפליקציות מסך הבית. כשהבית נפתח שום דבר אחר לא על המסך, וזה מנקה גם שאריות של אפליקציה שקרסה. */
    var homePackages: Set<String> = emptySet()

    private val resumed = LinkedHashSet<Pair<String, String>>()

    fun onEvent(packageName: String, className: String?, type: Int) {
        val key = packageName to className.orEmpty()
        when (type) {
            RESUMED -> {
                if (packageName in homePackages) resumed.clear()
                resumed.add(key)
            }
            PAUSED, STOPPED, DESTROYED -> resumed.remove(key)
        }
    }

    fun visiblePackages(): Set<String> = resumed.mapTo(LinkedHashSet()) { it.first }

    fun reset() = resumed.clear()

    companion object {
        // הערכים של UsageEvents.Event. לפני אנדרואיד 10 שני הראשונים נקראו MOVE_TO_FOREGROUND/BACKGROUND
        const val RESUMED = 1
        const val PAUSED = 2
        const val STOPPED = 23
        const val DESTROYED = 24
    }
}
