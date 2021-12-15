package dev.nmullaney.tesladashboard

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.nmullaney.tesladashboard.databinding.FragmentInfoBinding
import java.net.InetAddress

class InfoFragment() : Fragment() {
    private val TAG = InfoFragment::class.java.simpleName
    private lateinit var nsdManager: NsdManager
    private lateinit var binding : FragmentInfoBinding
    private lateinit var pandaInfo: NsdServiceInfo
    private lateinit var viewModel: DashViewModel
    private var zeroconfHost : String = ""



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(DashViewModel::class.java)

        //nsdManager?.resolveService(resolveListener)

        binding.toggleServerGroup.check(if (viewModel.useMockServer()) R.id.mock_server_button else R.id.real_server_button)

        binding.editIpAddress.text = SpannableStringBuilder(viewModel.serverIpAddress())

        binding.saveButton.setOnClickListener {
            viewModel.saveSettings(binding.toggleServerGroup.checkedButtonId == R.id.mock_server_button, binding.editIpAddress.text.toString())
        }
        binding.scanButton.setOnClickListener {

            viewModel.getZeroconfHost().observe(viewLifecycleOwner){
                viewModel.saveSettings(binding.toggleServerGroup.checkedButtonId == R.id.mock_server_button, it)
                binding.editIpAddress.text = SpannableStringBuilder(viewModel.serverIpAddress())
            }

        }
        binding.startButton.setOnClickListener {
            viewModel.startUp()
        }

        binding.stopButton.setOnClickListener {
            viewModel.shutdown()
        }

        binding.root.setOnLongClickListener {
            switchToDash()
        }
        binding.startDashButton.setOnClickListener(){
            switchToDash()
        }
        binding.scrollView.setOnLongClickListener{
            switchToDash()
        }

        viewModel.carState().observe(viewLifecycleOwner) { carState ->
            //logCarState(carState)


            binding.infoText.text = buildSpannedString {
                val sortedMap = viewModel.carStateHistory().toSortedMap()
                sortedMap.forEach() { entry ->
                    bold {
                        append(entry.key)
                        append(": ")
                    }
                    append(entry.value.value.toString())
                    append("\n")
                }
            }
        }
    }



    fun switchToDash() : Boolean {
        viewModel.switchToDashFragment()
        return true
    }

    fun logCarState(carState: CarState) {
        Log.d(TAG, "Car state size: " + carState.carData.size)
        carState.carData.forEach {
            Log.d(TAG, "Name: " + it.key + ", Value: " + it.value)
        }
    }
}
