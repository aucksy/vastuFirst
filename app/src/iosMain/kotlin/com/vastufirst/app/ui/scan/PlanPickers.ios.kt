@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vastufirst.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The iPhone's ways in for a plan picture (iPhone plan, stage 4).
 *
 * "Upload a plan" asks once where the plan is — Photos (most plans arrive as a WhatsApp or camera-roll
 * picture) or Files (a PDF from the builder) — because no single iPhone picker offers both, the way
 * Android's document picker does. "Photograph your plan" opens the camera; the first time, the iPhone
 * itself asks permission, with the sentence in Info.plist saying why.
 *
 * Delivered to the scan: an NSData (Photos), an NSURL to our own copy (Files), or a UIImage (camera) —
 * IosImageDecoder turns each into the same 1,400 px, quality-88 JPEG Android sends.
 */
@Composable
actual fun rememberPlanPickers(onPicked: (Any?) -> Unit): PlanPickers {
    val deliver by rememberUpdatedState(onPicked)
    return remember { IosPlanPickers { source -> deliver(source) } }
}

private class IosPlanPickers(private val deliver: (Any?) -> Unit) : PlanPickers {
    // UIKit holds its delegates weakly: keep the live one here until it has answered.
    private var delegate: NSObject? = null

    private fun finish(source: Any?) {
        delegate = null
        dispatch_async(dispatch_get_main_queue()) { deliver(source) }
    }

    override fun pickDocument() {
        val sheet = UIAlertController.alertControllerWithTitle(
            title = "Where is your plan?",
            message = null,
            preferredStyle = UIAlertControllerStyleActionSheet,
        )
        sheet.addAction(UIAlertAction.actionWithTitle("Photos", UIAlertActionStyleDefault) { _ -> openPhotos() })
        sheet.addAction(UIAlertAction.actionWithTitle("Files (PDF or picture)", UIAlertActionStyleDefault) { _ -> openFiles() })
        sheet.addAction(UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel) { _ -> finish(null) })
        present(sheet)
    }

    private fun openPhotos() {
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = config)
        val d = PhotosDelegate(::finish)
        delegate = d
        picker.delegate = d
        present(picker)
    }

    private fun openFiles() {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePDF, UTTypeImage),
            asCopy = true,
        )
        val d = FilesDelegate(::finish)
        delegate = d
        picker.delegate = d
        present(picker)
    }

    override fun takePhoto(): Boolean {
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(camera)) return false
        val picker = UIImagePickerController()
        picker.sourceType = camera
        val d = CameraDelegate(::finish)
        delegate = d
        picker.delegate = d
        present(picker)
        return true
    }

    private fun present(controller: UIViewController) {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null) ?: finish(null)
    }
}

private class PhotosDelegate(private val done: (Any?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return done(null)
        result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data: NSData?, _ ->
            done(data)
        }
    }
}

private class FilesDelegate(private val done: (Any?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        done(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = done(null)
}

private class CameraDelegate(private val done: (Any?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, null)
        done(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        done(null)
    }
}
