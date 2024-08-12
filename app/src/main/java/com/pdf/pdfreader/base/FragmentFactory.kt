package com.pdf.pdfreader.base



object FragmentFactory {

    fun <T : BaseFragment<*>> getFragment(aClass: Class<T>): T? {

        try {
            return aClass.getDeclaredConstructor().newInstance()
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }

        return null
    }
}
