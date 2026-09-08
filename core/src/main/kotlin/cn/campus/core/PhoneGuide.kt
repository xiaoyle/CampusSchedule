package cn.campus.core

enum class PhoneGuide(val label: String, val instruction: String) {
    HONOR("荣耀", "在设置中搜索“应用启动管理”，找到中大课表助手，检查自启动、关联启动和后台活动；再检查电池优化。"),
    HUAWEI("华为安卓", "在设置中搜索“应用启动管理”或“启动管理”，检查本应用的自启动和后台活动，并检查电池优化。本指引用于能够安装安卓应用的华为系统。"),
    XIAOMI("小米 / 红米", "在设置中搜索“自启动”或“后台自启动”，允许本应用；在应用电池设置中检查是否限制后台运行。菜单可能叫“省电策略”或“无限制”。"),
    OPPO("OPPO / 一加 / realme", "在设置中搜索“应用启动管理”或“自启动”，检查本应用；在应用耗电或电池管理中检查是否允许后台活动。"),
    VIVO("vivo / iQOO", "在设置或 i 管家中搜索“自启动”，检查本应用；在电池设置中搜索“后台耗电管理”，检查后台运行限制。"),
    SAMSUNG("三星", "在设置中搜索“后台使用限制”，检查本应用是否被加入休眠或深度休眠应用；在本应用的电池设置中检查后台限制。"),
    OTHER("其他安卓", "在系统设置中搜索“自启动”“后台运行”或“电池优化”，检查中大课表助手的限制；没有对应开关时，以系统提供的设置为准。" );

    companion object {
        fun detect(manufacturer: String, brand: String): PhoneGuide {
            val values = listOf(manufacturer, brand).map { it.trim().lowercase(java.util.Locale.ROOT) }
            fun has(vararg names: String) = values.any { it in names }
            return when {
                has("honor", "荣耀") -> HONOR
                has("huawei", "华为") -> HUAWEI
                has("xiaomi", "redmi", "poco") -> XIAOMI
                has("oppo", "oneplus", "realme") -> OPPO
                has("vivo", "iqoo") -> VIVO
                has("samsung") -> SAMSUNG
                else -> OTHER
            }
        }
    }
}
