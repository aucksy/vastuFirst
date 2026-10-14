package com.vastufirst.app.ui.scan

import com.vastufirst.shared.scan.PlanImage

/** The decoded plan picture's pixels, for the wall snap. Null when the image will not decode. */
expect fun DecodedImage.toPlanImage(): PlanImage?
