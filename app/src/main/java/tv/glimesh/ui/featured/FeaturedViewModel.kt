package tv.glimesh.ui.featured

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FeaturedViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is the featured channels Fragment"
    }
    val text: LiveData<String> = _text
}
