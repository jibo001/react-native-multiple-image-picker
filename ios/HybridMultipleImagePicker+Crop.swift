//
//  HybridMultipleImagePicker+Crop.swift
//  Pods
//
//  Created by BAO HA on 9/12/24.
//

import HXPhotoPicker

extension HybridMultipleImagePicker {
    func openCrop(image: String, config: NitroCropConfig, resolved: @escaping ((CropResult) -> Void), rejected: @escaping ((Double) -> Void)) throws {
        let asset: EditorAsset

        if image.hasPrefix("http://") || image.hasPrefix("https://") || image.hasPrefix("file://") {
            guard let url = URL(string: image),
                  let data = try? Data(contentsOf: url)

            else {
                rejected(0)
                return
            }

            asset = .init(type: .imageData(data))
        } else {
            asset = .init(type: .photoAsset(.init(localIdentifier: image)))
        }

        let cropOption = PickerCropConfig(circle: config.circle, ratio: config.ratio, defaultRatio: config.defaultRatio, freeStyle: config.freeStyle)

        #if HXPICKER_ENABLE_EDITOR
        var editConfig = setCropConfig(cropOption)
        #else
        var editConfig = EditorConfiguration()
        #endif

        editConfig.languageType = setLocale(language: config.language)

        DispatchQueue.main.async {
            Photo.edit(asset: asset, config: editConfig) { result, _ in

                if let path = result.result?.url.absoluteString, let size = result.result?.image?.size {
                    let result = CropResult(path: path, width: size.width, height: size.height)

                    resolved(result)
                }
            }
        }
    }

    #if HXPICKER_ENABLE_EDITOR
    func setCropConfig(_ cropConfig: PickerCropConfig) -> EditorConfiguration {
        var config = EditorConfiguration()

        if let defaultRatio = cropConfig.defaultRatio {
            config.cropSize.aspectRatio = .init(width: defaultRatio.width, height: defaultRatio.height)
        }

        config.photo.defaultSelectedToolOption = .cropSize

        config.isFixedCropSizeState = true

        config.cropSize.defaultSeletedIndex = 0

        let freeStyle = cropConfig.freeStyle ?? true

        config.cropSize.isFixedRatio = !freeStyle

        config.isWhetherFinishButtonDisabledInUneditedState = true

        config.cropSize.isRoundCrop = cropConfig.circle ?? false

        config.cropSize.isResetToOriginal = true

        // 使用默认工具栏配置，但隐藏贴纸和配乐按钮
        config.toolsView = .default
        // 过滤掉贴纸和配乐按钮
        config.toolsView.toolOptions = config.toolsView.toolOptions.filter { $0.type != .chartlet && $0.type != .music }

        config.photo.defaultSelectedToolOption = .cropSize

        if config.cropSize.isRoundCrop {
            config.cropSize.aspectRatios = []
        } else {
            var aspectRatios: [EditorRatioToolConfig] = PickerConfiguration.default.editor.cropSize.aspectRatios

            let ratio = cropConfig.ratio
            // custom ratio
            if ratio.count > 0 {
                ratio.forEach { ratio in
                    let width = Int(ratio.width)
                    let height = Int(ratio.height)

                    aspectRatios.insert(.init(title: .custom(ratio.title ?? "\(width)/\(height)"), ratio: .init(width: width, height: height)), at: 3)
                }
            }

            config.cropSize.aspectRatios = freeStyle ? aspectRatios : aspectRatios.filter {
                // check freeStyle crop
                if $0.ratio == .zero { return false }

                return true
            }
        }

        return config
    }
    #endif
}
