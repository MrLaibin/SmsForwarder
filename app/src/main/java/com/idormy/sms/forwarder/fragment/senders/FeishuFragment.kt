package com.idormy.sms.forwarder.fragment.senders

import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.viewModels
import com.google.gson.Gson
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.database.AppDatabase
import com.idormy.sms.forwarder.database.entity.Sender
import com.idormy.sms.forwarder.database.viewmodel.BaseViewModelFactory
import com.idormy.sms.forwarder.database.viewmodel.SenderViewModel
import com.idormy.sms.forwarder.databinding.FragmentSendersFeishuBinding
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.setting.FeishuSetting
import com.idormy.sms.forwarder.utils.*
import com.idormy.sms.forwarder.utils.sender.FeishuUtils
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.DialogAction
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*

@Page(name = "飞书机器人")
@Suppress("PrivatePropertyName")
class FeishuFragment : BaseFragment<FragmentSendersFeishuBinding?>(), View.OnClickListener {

    private val TAG: String = FeishuFragment::class.java.simpleName
    var titleBar: TitleBar? = null
    private val viewModel by viewModels<SenderViewModel> { BaseViewModelFactory(context) }

    @JvmField
    @AutoWired(name = KEY_SENDER_ID)
    var senderId: Long = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_TYPE)
    var senderType: Int = 0

    @JvmField
    @AutoWired(name = KEY_SENDER_CLONE)
    var isClone: Boolean = false

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentSendersFeishuBinding {
        return FragmentSendersFeishuBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.feishu)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        //新增
        if (senderId <= 0) {
            titleBar?.setSubTitle(getString(R.string.add_sender))
            binding!!.btnDel.setText(R.string.discard)
            return
        }

        //编辑
        binding!!.btnDel.setText(R.string.del)
        AppDatabase.getInstance(requireContext())
            .senderDao()
            .get(senderId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Sender> {
                override fun onSubscribe(d: Disposable) {}

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }

                override fun onSuccess(sender: Sender) {
                    if (isClone) {
                        titleBar?.setSubTitle(getString(R.string.clone_sender) + ": " + sender.name)
                        binding!!.btnDel.setText(R.string.discard)
                    } else {
                        titleBar?.setSubTitle(getString(R.string.edit_sender) + ": " + sender.name)
                    }
                    binding!!.etName.setText(sender.name)
                    binding!!.sbEnable.isChecked = sender.status == 1
                    val settingVo = Gson().fromJson(sender.jsonSetting, FeishuSetting::class.java)
                    Log.d(TAG, settingVo.toString())
                    if (settingVo != null) {
                        binding!!.etWebhook.setText(settingVo.webhook)
                        binding!!.etSecret.setText(settingVo.secret)
                        binding!!.rgMsgType.check(settingVo.getMsgTypeCheckId())
                        binding!!.etTitleTemplate.setText(settingVo.titleTemplate)
                    }
                }
            })
    }

    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            val etTitleTemplate: EditText = binding!!.etTitleTemplate
            when (v.id) {
                R.id.bt_insert_sender -> {
                    CommonUtils.insertOrReplaceText2Cursor(etTitleTemplate, getString(R.string.tag_from))
                    return
                }
                R.id.bt_insert_extra -> {
                    CommonUtils.insertOrReplaceText2Cursor(etTitleTemplate, getString(R.string.tag_card_slot))
                    return
                }
                R.id.bt_insert_time -> {
                    CommonUtils.insertOrReplaceText2Cursor(etTitleTemplate, getString(R.string.tag_receive_time))
                    return
                }
                R.id.bt_insert_device_name -> {
                    CommonUtils.insertOrReplaceText2Cursor(etTitleTemplate, getString(R.string.tag_device_name))
                    return
                }
                R.id.btn_test -> {
                    val settingVo = checkSetting()
                    Log.d(TAG, settingVo.toString())
                    val msgInfo = MsgInfo("sms", getString(R.string.test_phone_num), getString(R.string.test_sender_sms), Date(), getString(R.string.test_sim_info))
                    FeishuUtils.sendMsg(settingVo, msgInfo)
                    return
                }
                R.id.btn_del -> {
                    if (senderId <= 0 || isClone) {
                        popToBack()
                        return
                    }

                    MaterialDialog.Builder(requireContext())
                        .title(R.string.delete_sender_title)
                        .content(R.string.delete_sender_tips)
                        .positiveText(R.string.lab_yes)
                        .negativeText(R.string.lab_no)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            viewModel.delete(senderId)
                            XToastUtils.success(R.string.delete_sender_toast)
                            popToBack()
                        }
                        .show()
                    return
                }
                R.id.btn_save -> {
                    val name = binding!!.etName.text.toString().trim()
                    if (TextUtils.isEmpty(name)) {
                        throw Exception(getString(R.string.invalid_name))
                    }

                    val status = if (binding!!.sbEnable.isChecked) 1 else 0
                    val settingVo = checkSetting()
                    if (isClone) senderId = 0
                    val senderNew = Sender(senderId, senderType, name, Gson().toJson(settingVo), status)
                    Log.d(TAG, senderNew.toString())

                    viewModel.insertOrUpdate(senderNew)
                    XToastUtils.success(R.string.tipSaveSuccess)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString())
            e.printStackTrace()
        }
    }

    private fun checkSetting(): FeishuSetting {
        val webhook = binding!!.etWebhook.text.toString().trim()
        if (!CommonUtils.checkUrl(webhook, false)) {
            throw Exception(getString(R.string.invalid_webhook))
        }

        val secret = binding!!.etSecret.text.toString().trim()
        val msgType = if (binding!!.rgMsgType.checkedRadioButtonId == R.id.rb_msg_type_interactive) "interactive" else "text"
        val title = binding!!.etTitleTemplate.text.toString().trim()

        return FeishuSetting(webhook, secret, msgType, title)
    }

}