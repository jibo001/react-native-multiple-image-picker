//
//  HybridMultipleImagePicker.swift
//
//  Created by Marc Rousavy on 18.07.24.
//

import Foundation
import HXPhotoPicker
import NitroModules
import Photos

class HybridMultipleImagePicker: HybridMultipleImagePickerSpec {
    var selectedAssets: [PhotoAsset] = .init()

    var config: PickerConfiguration = .init()
    private var legacyStatusBarStyleBeforePicker: UIStatusBarStyle?

    func openPicker(config: NitroConfig, resolved: @escaping (([PickerResult]) -> Void), rejected: @escaping ((Double) -> Void)) throws {
        setConfig(config)

        // get selected photo
        selectedAssets = selectedAssets.filter { asset in
            config.selectedAssets.contains {
                $0.localIdentifier == asset.phAsset?.localIdentifier
            }
        }

        DispatchQueue.main.async {
            self.prepareStatusBarForPickerPresentation()
            Photo.picker(
                self.config,
                selectedAssets: self.selectedAssets
            ) { pickerResult, controller in

                controller.autoDismiss = false

                // check crop for single
                if let asset = pickerResult.photoAssets.first, config.selectMode == .single, config.crop != nil, asset.mediaType == .photo, asset.editedResult?.url == nil {
                    // open crop
                    Photo.edit(asset: .init(type: .photoAsset(asset)), config: self.config.editor, sender: controller) { editedResult, _ in

                        if let photoAsset = pickerResult.photoAssets.first, let result = editedResult.result {
                            photoAsset.editedResult = .some(result)

                            Task {
                                let resultData = try await self.getResult(photoAsset)

                                DispatchQueue.main.async {
                                    resolved([resultData])
                                    controller.dismiss(true) {
                                        self.restoreStatusBarAfterPickerDismissal()
                                    }
                                }
                            }
                        }
                    }

                    return
                }

                // show alert view
                let alert = UIAlertController(title: nil, message: "Loading...", preferredStyle: .alert)
                alert.showLoading()
                controller.present(alert, animated: true)

                let group = DispatchGroup()

                var data: [PickerResult] = []

                self.selectedAssets = pickerResult.photoAssets

                Task {
                    for response in pickerResult.photoAssets {
                        group.enter()

                        let resultData = try await self.getResult(response)

                        data.append(resultData)
                        group.leave()
                    }

                    DispatchQueue.main.async {
                        alert.dismiss(animated: true) {
                            controller.dismiss(true) {
                                self.restoreStatusBarAfterPickerDismissal()
                                resolved(data)
                            }
                        }
                    }
                }

            } cancel: { cancel in
                cancel.autoDismiss = true
                self.restoreStatusBarAfterPickerDismissal()
                rejected(0.0)
            }
        }
    }

    private func prepareStatusBarForPickerPresentation() {
        applyLegacyStatusBarStyleIfNeeded(.lightContent)
        getTopViewController()?.setNeedsStatusBarAppearanceUpdate()
    }

    private func restoreStatusBarAfterPickerDismissal() {
        restoreLegacyStatusBarStyleIfNeeded()
        getTopViewController()?.setNeedsStatusBarAppearanceUpdate()
    }

    private func isViewControllerBasedStatusBarAppearanceEnabled() -> Bool {
        if let value = Bundle.main.object(forInfoDictionaryKey: "UIViewControllerBasedStatusBarAppearance") as? Bool {
            return value
        }
        return true
    }

    private func applyLegacyStatusBarStyleIfNeeded(_ style: UIStatusBarStyle) {
        guard !isViewControllerBasedStatusBarAppearanceEnabled() else {
            return
        }
        legacyStatusBarStyleBeforePicker = UIApplication.shared.statusBarStyle
        UIApplication.shared.statusBarStyle = style
    }

    private func restoreLegacyStatusBarStyleIfNeeded() {
        guard !isViewControllerBasedStatusBarAppearanceEnabled() else {
            return
        }
        if let previousStyle = legacyStatusBarStyleBeforePicker {
            UIApplication.shared.statusBarStyle = previousStyle
        }
        legacyStatusBarStyleBeforePicker = nil
    }
}

extension UIAlertController {
    func showLoading() {
        let loadingIndicator = UIActivityIndicatorView(frame: CGRect(x: 10, y: 5, width: 50, height: 50))

        loadingIndicator.hidesWhenStopped = true
        loadingIndicator.style = UIActivityIndicatorView.Style.medium

        if #available(iOS 13.0, *) {
            loadingIndicator.color = .secondaryLabel
        } else {
            loadingIndicator.color = .black
        }

        loadingIndicator.startAnimating()

        view.addSubview(loadingIndicator)
    }
}
