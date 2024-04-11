package otus.homework.reactivecats

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatsViewModel(
    private val catsService: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    context: Context
) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    private val _catsLiveData = MutableLiveData<Result>()
    val catsLiveData: LiveData<Result> = _catsLiveData

    init {
        compositeDisposable.add(
            catsService.getCatFact()
                .subscribeOn(Schedulers.io())
                .map<Result>{ fact -> Success(fact) }
                .onErrorReturn { th ->
                    return@onErrorReturn when(th) {
                        is HttpException -> {
                            Error(th.message())
                        }
                        is IOException -> {
                            ServerError
                        }
                        else -> {
                            Error(th.message ?: context.getString(R.string.default_error_text))
                        }
                    }
                }
                .subscribe {
                    _catsLiveData.postValue(it)
                }
        )
    }

    fun getFacts() {
        compositeDisposable.add(
            Observable.interval(2000, TimeUnit.MILLISECONDS)
                .flatMap {
                    catsService.getCatFact()
                        .onErrorResumeNext(localCatFactsGenerator.generateCatFact())
                }
                .map<Result> { fact -> Success(fact) }
                .subscribe {
                    _catsLiveData.postValue(it)
                }
        )
    }

    override fun onCleared() {
        compositeDisposable.dispose()
        super.onCleared()
    }
}

class CatsViewModelFactory(
    private val catsRepository: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    private val context: Context
) :
    ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CatsViewModel(catsRepository, localCatFactsGenerator, context) as T
}

sealed class Result
data class Success(val fact: Fact) : Result()
data class Error(val message: String) : Result()
object ServerError : Result()