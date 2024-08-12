package com.pdf.pdfreader.di

import android.content.Context
import com.pdf.pdfreader.base.BaseActivity
import com.pdf.pdfreader.base.DiConstants
import com.pdf.pdfreader.base.FragmentHandler
import com.pdf.pdfreader.base.FragmentManager
import com.pdf.pdfreader.base.Navigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Named

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    @ActivityScoped
    internal fun provideBaseActivity(@ActivityContext context: Context): BaseActivity {
        return (context as BaseActivity)
    }

    @Provides
    @ActivityScoped
    internal fun provideNavigator(baseActivity: BaseActivity): Navigator {
        return baseActivity
    }

    @Provides
    @ActivityScoped
    internal fun provideFragmentHandler(fragmentManager: FragmentManager): FragmentHandler {
        return fragmentManager
    }

    @Provides
    @ActivityScoped
    @Named(DiConstants.PLACEHOLDER)
    internal fun providePlaceHolder(baseActivity: BaseActivity): Int {
        return baseActivity.findFragmentPlaceHolder()
    }
}