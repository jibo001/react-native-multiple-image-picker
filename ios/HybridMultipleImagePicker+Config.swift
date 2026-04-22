//
//  HybridMultipleImagePicker+Config.swift
//  react-native-multiple-image-picker
//
//  Created by BAO HA on 15/10/2024.
//

import HXPhotoPicker
import UIKit
import Photos

// Swift enum
// @objc enum MediaType: SelectBoxView.Style

extension HybridMultipleImagePicker {
    func setConfig(_ options: NitroConfig) {
        config = PickerConfiguration.default

        var photoList = config.photoList
        var previewView = config.previewView
        let isLimitedLibraryAccess = self.isLimitedPhotoLibraryAccess()
        let shouldLoadPhotoLibraryImmediately = self.shouldLoadPhotoLibraryImmediately()

        // Do not trigger photo-library permission prompt on first entry.
        // Permission will be requested only when user takes an explicit action.
        config.allowLoadPhotoLibrary = shouldLoadPhotoLibraryImmediately

        if let spacing = options.spacing { photoList.spacing = spacing }
        if let rowNumber = options.numberOfColumn { photoList.rowNumber = Int(rowNumber) }

        // 强制显示预览按钮，忽略isHiddenPreviewButton配置
        // if let isHiddenPreviewButton = options.isHiddenPreviewButton {
        //     previewView.bottomView.isHiddenPreviewButton = isHiddenPreviewButton
        //     photoList.bottomView.isHiddenOriginalButton = isHiddenPreviewButton
        // }

        if let isHiddenOriginalButton = options.isHiddenOriginalButton {
            previewView.bottomView.isHiddenOriginalButton = isHiddenOriginalButton
            photoList.bottomView.isHiddenOriginalButton = isHiddenOriginalButton
        }

        photoList.allowHapticTouchPreview = options.allowHapticTouchPreview ?? true

        photoList.allowSwipeToSelect = options.allowSwipeToSelect ?? true

        photoList.allowAddLimit = options.allowedLimit ?? true

        // check media type
        switch options.mediaType {
        case .image:
            config.selectOptions = [.photo, .livePhoto]
        case .video:
            config.selectOptions = .video
        default:
            config.selectOptions = [.video, .photo, .livePhoto]
        }

        config.indicatorType = .system
        config.photoList.cell.kf_indicatorColor = .black

        if let boxStyle = SelectBoxView.Style(rawValue: Int(options.selectBoxStyle.rawValue)) {
            previewView.selectBox.style = boxStyle
            photoList.cell.selectBox.style = boxStyle
        }

        photoList.isShowFilterItem = false
        if isLimitedLibraryAccess && photoList.allowAddLimit {
            photoList.sort = .asc
            photoList.limitCell.title = getAddMoreLimitTitle(options)
        } else {
            photoList.sort = .desc
        }
        // Make the "+" icon slimmer for the limited-access add-more cell.
        photoList.limitCell.lineWidth = 2
        photoList.limitCell.lineLength = 24
        photoList.isShowAssetNumber = false

        previewView.disableFinishButtonWhenNotSelected = false

        if let selectMode = PickerSelectMode(rawValue: Int(options.selectMode.rawValue)) {
            config.selectMode = selectMode
        }

        if let maxFileSize = options.maxFileSize {
            config.maximumSelectedPhotoFileSize = Int(maxFileSize)
            config.maximumSelectedVideoFileSize = Int(maxFileSize)
        }

        // Setting for video
        if options.mediaType == .all || options.mediaType == .video {
            if let maxVideo = options.maxVideo {
                config.maximumSelectedVideoCount = Int(maxVideo)
            }

            if let maxVideoDuration = options.maxVideoDuration {
                config.maximumSelectedVideoDuration = Int(maxVideoDuration)
            }

            if let minVideoDuration = options.minVideoDuration {
                config.minimumSelectedVideoDuration = Int(minVideoDuration)
            }
        }

        if let maxSelect = options.maxSelect {
            config.maximumSelectedCount = Int(maxSelect)
        }

        config.allowSyncICloudWhenSelectPhoto = true

        config.allowCustomTransitionAnimation = true

        config.isSelectedOriginal = false

        let isPreview = options.isPreview ?? true

        // 确保底部视图正确显示
        photoList.bottomView.isShowSelectedView = true
        previewView.bottomView.isShowSelectedView = true
        
        previewView.bottomView.isShowPreviewList = isPreview
        // 强制显示预览按钮，不依赖isPreview配置
        photoList.bottomView.isHiddenPreviewButton = false
        previewView.bottomView.isHiddenPreviewButton = false
        photoList.allowHapticTouchPreview = isPreview
        photoList.bottomView.previewListTickColor = .clear
        photoList.bottomView.isShowSelectedView = isPreview

        if isPreview {
            config.videoSelectionTapAction = .preview
            config.photoSelectionTapAction = .preview
        } else {
            config.videoSelectionTapAction = .quickSelect
            config.photoSelectionTapAction = .quickSelect
        }

        // 强制启用编辑功能
        config.editorOptions = [.photo, .video, .livePhoto]
        
        // 始终显示编辑按钮
        previewView.bottomView.isHiddenEditButton = false

        if let crop = options.crop {
            #if HXPICKER_ENABLE_EDITOR
            var editorConfig = setCropConfig(crop)
            // 强制隐藏贴纸和配乐按钮，保留其他编辑功能
            editorConfig.toolsView.toolOptions = editorConfig.toolsView.toolOptions.filter { $0.type != .chartlet && $0.type != .music }
            config.editor = editorConfig
            #else
            // 如果没有启用编辑功能，使用默认配置
            config.editor = EditorConfiguration()
            #endif
        } else {
            #if HXPICKER_ENABLE_EDITOR
            // 即使没有crop配置，也启用编辑功能
            let defaultCropConfig = PickerCropConfig(circle: false, ratio: [], defaultRatio: nil, freeStyle: true)
            var editorConfig = setCropConfig(defaultCropConfig)
            // 强制隐藏贴纸和配乐按钮，保留其他编辑功能
            editorConfig.toolsView.toolOptions = editorConfig.toolsView.toolOptions.filter { $0.type != .chartlet && $0.type != .music }
            config.editor = editorConfig
            #else
            // 如果没有启用编辑功能，使用默认配置
            config.editor = EditorConfiguration()
            #endif
        }
        
        // 最后再次强制确保贴纸和配乐按钮被隐藏
        config.editor.toolsView.toolOptions = config.editor.toolsView.toolOptions.filter { $0.type != .chartlet && $0.type != .music }

        photoList.finishSelectionAfterTakingPhoto = true

        if let cameraOption = options.camera {
            photoList.allowAddCamera = true

            photoList.cameraType = .system(setCameraConfig(cameraOption))
        } else {
            photoList.allowAddCamera = false
        }

        if isLimitedLibraryAccess && photoList.allowAddLimit {
            // Keep the "add more photos" entry as the last cell in limited mode.
            photoList.allowAddCamera = false
        }

        config.photoList = photoList
        config.previewView = previewView

        setLanguage(options)
        setTheme(options)

        // 最后再次强制设置预览和编辑按钮显示，确保不被其他配置覆盖
        config.previewView.bottomView.isHiddenPreviewButton = false
        config.photoList.bottomView.isHiddenPreviewButton = false
        config.previewView.bottomView.isHiddenEditButton = false
        
        // 确保底部视图的其他必要配置
        config.photoList.bottomView.isShowSelectedView = true
        config.previewView.bottomView.isShowSelectedView = true

        config.modalPresentationStyle = setPresentation(options.presentation)
    }

    private func setTheme(_ options: NitroConfig) {
        let isDark = options.theme == Theme.dark

        // custom background dark
        if let background = options.backgroundDark, let backgroundDark = getReactColor(Int(background)), isDark {
            config.photoList.backgroundDarkColor = backgroundDark
            config.photoList.backgroundColor = backgroundDark
        }

        if isDark {
            let background = UIColor(hex: "#1E1F22")
            let titleBackground = UIColor(hex: "#3F4043")
            let albumBackground = UIColor(hex: "#2C2D30")

            config.statusBarStyle = .lightContent
            config.appearanceStyle = .dark
            config.navigationBarStyle = .black

            config.photoList.backgroundColor = background
            config.photoList.backgroundDarkColor = background
            config.photoList.titleView.backgroundColor = titleBackground
            config.photoList.emptyView.titleColor = .white
            config.photoList.emptyView.subTitleColor = UIColor(hex: "#A9A9AA")

            config.previewView.backgroundColor = background
            config.previewView.backgroundDarkColor = background
            config.previewView.statusBarHiddenBgColor = background

            config.photoList.bottomView.barStyle = .black
            config.previewView.bottomView.barStyle = .black
            config.photoList.bottomView.backgroundColor = background
            config.photoList.bottomView.backgroundDarkColor = background
            config.previewView.bottomView.backgroundColor = background
            config.previewView.bottomView.backgroundDarkColor = background

            config.albumList.backgroundColor = albumBackground
            config.albumList.cellBackgroundColor = albumBackground
            config.albumList.albumNameColor = .white
            config.albumList.photoCountColor = UIColor(hex: "#A9A9AA")
            config.albumList.cellSelectedColor = UIColor(hex: "#3A3B3E")
            config.albumList.separatorLineColor = UIColor(hex: "#3A3B3E")

            // WeChat-like prompt color in limited mode.
            let promptIconColor = UIColor(hex: "#E2B322")
            let promptTextColor = UIColor(hex: "#8E8E93")
            config.photoList.bottomView.promptIconColor = promptIconColor
            config.photoList.bottomView.promptIconDarkColor = promptIconColor
            config.photoList.bottomView.promptTitleColor = promptTextColor
            config.photoList.bottomView.promptTitleDarkColor = promptTextColor
            config.photoList.bottomView.promptArrowColor = promptTextColor
            config.photoList.bottomView.promptArrowDarkColor = promptTextColor

            if options.primaryColor == nil {
                config.setThemeColor(UIColor(hex: "#07C160"))
            }
        } else {
            let background = UIColor.white
            let barStyle = UIBarStyle.default

            config.statusBarStyle = .darkContent
            config.appearanceStyle = .normal
            config.photoList.bottomView.barStyle = barStyle
            config.navigationBarStyle = barStyle
            config.previewView.bottomView.barStyle = barStyle
            config.previewView.backgroundColor = background
            config.previewView.bottomView.backgroundColor = background

            config.photoList.leftNavigationItems = [PhotoCancelItem.self]

            config.photoList.backgroundColor = .white
            config.photoList.emptyView.titleColor = .black
            config.photoList.emptyView.subTitleColor = .darkGray
            config.photoList.titleView.backgroundColor = UIColor.black.withAlphaComponent(0.5)

            config.albumList.backgroundColor = .white
            config.albumList.cellBackgroundColor = .white
            config.albumList.albumNameColor = .black
            config.albumList.photoCountColor = .black
            config.albumList.cellSelectedColor = "#e1e1e1".hx.color
            config.albumList.separatorLineColor = "#e1e1e1".hx.color
        }

        if let primaryColor = options.primaryColor, let color = getReactColor(Int(primaryColor)) {
            config.setThemeColor(color)
        }

        applySendButtonStyle(&config.photoList.bottomView)
        applySendButtonStyle(&config.previewView.bottomView)
        applyOriginalSelectBoxStyle(&config.photoList.bottomView)
        applyOriginalSelectBoxStyle(&config.previewView.bottomView)
        applyWhiteAccentStyle()
        logIfAutoLimitedAlertMayAppear()

        // Keep picker status bar icons white while presenting the picker.
        // When picker dismisses, iOS restores the host page status bar automatically.
        config.statusBarStyle = .lightContent
        config.navigationTitleColor = .white
        config.photoList.cell.customSelectableCellClass = nil
    }

    private func applySendButtonStyle(_ bottomView: inout PickerBottomViewConfiguration) {
        bottomView.finishButtonTitleColor = .black
        bottomView.finishButtonTitleDarkColor = .black
        bottomView.finishButtonBackgroundColor = .white
        bottomView.finishButtonDarkBackgroundColor = .white
        bottomView.finishButtonDisableTitleColor = UIColor.black.withAlphaComponent(0.45)
        bottomView.finishButtonDisableTitleDarkColor = UIColor.black.withAlphaComponent(0.45)
        bottomView.finishButtonDisableBackgroundColor = UIColor.white.withAlphaComponent(0.35)
        bottomView.finishButtonDisableDarkBackgroundColor = UIColor.white.withAlphaComponent(0.35)
    }

    private func applyOriginalSelectBoxStyle(_ bottomView: inout PickerBottomViewConfiguration) {
        // Unselected state: transparent center.
        bottomView.originalSelectBox.backgroundColor = .clear
        bottomView.originalSelectBox.darkBackgroundColor = .clear
        bottomView.originalSelectBox.borderColor = .white
        bottomView.originalSelectBox.borderDarkColor = .white
        // Selected state: white circle with black tick icon.
        bottomView.originalSelectBox.selectedBackgroundColor = .white
        bottomView.originalSelectBox.selectedBackgroudDarkColor = .white
        bottomView.originalSelectBox.tickColor = .black
        bottomView.originalSelectBox.tickDarkColor = .black
    }

    private func applyWhiteAccentStyle() {
        let white = UIColor.white
        let black = UIColor.black

        // Preview back/cancel button tint.
        config.navigationTintColor = white
        config.navigationDarkTintColor = white

        // Top title arrow capsule: remove blue accent.
        config.photoList.titleView.arrow.backgroundColor = white
        config.photoList.titleView.arrow.backgroudDarkColor = white
        config.photoList.titleView.arrow.arrowColor = black
        config.photoList.titleView.arrow.arrowDarkColor = black

        // Album list selected tick: white instead of blue.
        config.albumList.tickColor = white
        config.albumList.tickDarkColor = white

        // Bottom limited-permission prompt icon/arrow: white instead of blue.
        config.photoList.bottomView.promptIconColor = white
        config.photoList.bottomView.promptIconDarkColor = white
        config.photoList.bottomView.promptArrowColor = white
        config.photoList.bottomView.promptArrowDarkColor = white
        config.photoList.bottomView.promptTitleColor = UIColor.white.withAlphaComponent(0.65)
        config.photoList.bottomView.promptTitleDarkColor = UIColor.white.withAlphaComponent(0.65)

        // Selection checkboxes (grid + preview): white background with black tick.
        config.photoList.cell.selectBox.selectedBackgroundColor = white
        config.photoList.cell.selectBox.selectedBackgroudDarkColor = white
        config.photoList.cell.selectBox.tickColor = black
        config.photoList.cell.selectBox.tickDarkColor = black
        config.photoList.cell.selectBox.titleColor = black
        config.photoList.cell.selectBox.titleDarkColor = black
        config.previewView.selectBox.selectedBackgroundColor = white
        config.previewView.selectBox.selectedBackgroudDarkColor = white
        config.previewView.selectBox.tickColor = black
        config.previewView.selectBox.tickDarkColor = black
        config.previewView.selectBox.titleColor = black
        config.previewView.selectBox.titleDarkColor = black
        config.previewView.selectBox.style = config.photoList.cell.selectBox.style
    }

    private func logIfAutoLimitedAlertMayAppear() {
        guard #available(iOS 14, *) else {
            return
        }
        guard PHPhotoLibrary.authorizationStatus(for: .readWrite) == .limited else {
            return
        }
        if !hasPreventAutomaticLimitedAccessAlertEnabled() {
            NSLog("[MultipleImagePicker] PHPhotoLibraryPreventAutomaticLimitedAccessAlert is not enabled in host app Info.plist. To avoid iOS auto-showing the limited-access system alert, picker will not auto-load library in limited mode.")
        }
    }

    func setPresentation(_ presentation: Presentation?) -> UIModalPresentationStyle {
        if let presentation {
            switch Int(presentation.rawValue) {
            case 1:
                return .formSheet
            default:
                return .fullScreen
            }
        }

        return .fullScreen
    }

    private func isLimitedPhotoLibraryAccess() -> Bool {
        guard #available(iOS 14, *) else {
            return false
        }
        return PHPhotoLibrary.authorizationStatus(for: .readWrite) == .limited
    }

    private func shouldLoadPhotoLibraryImmediately() -> Bool {
        if #available(iOS 14, *) {
            let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            if status == .notDetermined {
                return false
            }
            // In limited mode, iOS may auto-show a system alert every app launch unless the host app
            // opts in with PHPhotoLibraryPreventAutomaticLimitedAccessAlert=true.
            // Skip eager library loading when that key is missing to avoid unexpected popups.
            if status == .limited && !hasPreventAutomaticLimitedAccessAlertEnabled() {
                return false
            }
            return true
        }
        return PHPhotoLibrary.authorizationStatus() != .notDetermined
    }

    private func hasPreventAutomaticLimitedAccessAlertEnabled() -> Bool {
        return (Bundle.main.object(forInfoDictionaryKey: "PHPhotoLibraryPreventAutomaticLimitedAccessAlert") as? Bool) == true
    }

    private func getAddMoreLimitTitle(_ options: NitroConfig) -> String {
        if let custom = options.text?.addMore?.trimmingCharacters(in: .whitespacesAndNewlines), !custom.isEmpty {
            return custom
        }
        let language = resolveLanguage(options.language)
        switch language {
        case .zhHans:
            return "添加更多\n可访问照片"
        case .zhHant:
            return "新增更多\n可存取照片"
        case .ja:
            return "さらに追加\nアクセス可能な写真"
        case .ko:
            return "더 추가\n접근 가능한 사진"
        default:
            return "Add More\nAccessible Photos"
        }
    }

    private func setLanguage(_ options: NitroConfig) {
        if let text = options.text {
            if let finish = text.finish {
                config.textManager.picker.photoList.bottomView.finishTitle = .custom(finish)
                config.textManager.picker.preview.bottomView.finishTitle = .custom(finish)
                config.textManager.editor.crop.maskListFinishTitle = .custom(finish)
            }

            if let original = text.original {
                config.textManager.picker.photoList.bottomView.originalTitle = .custom(original)
                config.textManager.picker.preview.bottomView.originalTitle = .custom(original)
            }

            if let preview = text.preview {
                config.textManager.picker.photoList.bottomView.previewTitle = .custom(preview)
            }

            if let edit = text.edit {
                config.textManager.picker.preview.bottomView.editTitle = .custom(edit)
            }
        }

        config.languageType = setLocale(language: options.language)
    }

    func setLocale(language: Language) -> LanguageType {
        let effectiveLanguage = resolveLanguage(language)
        switch effectiveLanguage {
        case .vi:
            return .vietnamese // -> 🇻🇳 My country. Yeahhh
        case .zhHans:
            return .simplifiedChinese
        case .zhHant:
            return .traditionalChinese
        case .ja:
            return .japanese
        case .ko:
            return .korean
        case .en:
            return .english
        case .th:
            return .thai
        case .id:
            return .indonesia
        case .ru:
            return .russian
        case .de:
            return .german
        case .fr:
            return .french
        case .ar:
            return .arabic
        default:
            return .system
        }
    }

    private func resolveLanguage(_ language: Language) -> Language {
        guard language == .system else {
            return language
        }

        if let preferred = Locale.preferredLanguages.first,
           let mapped = mapLanguageIdentifier(preferred) {
            return mapped
        }

        if let mapped = mapLanguageIdentifier(Locale.current.identifier) {
            return mapped
        }

        if let appLocale = Bundle.main.preferredLocalizations.first,
           let mapped = mapLanguageIdentifier(appLocale) {
            return mapped
        }

        return .system
    }

    private func mapLanguageIdentifier(_ identifier: String) -> Language? {
        let value = identifier.lowercased()
        if value.hasPrefix("zh-hant") || value.hasPrefix("zh-tw") || value.hasPrefix("zh-hk") || value.hasPrefix("zh-mo") {
            return .zhHant
        }
        if value.hasPrefix("zh") {
            return .zhHans
        }
        if value.hasPrefix("ja") {
            return .ja
        }
        if value.hasPrefix("ko") {
            return .ko
        }
        if value.hasPrefix("fr") {
            return .fr
        }
        if value.hasPrefix("de") {
            return .de
        }
        if value.hasPrefix("ru") {
            return .ru
        }
        if value.hasPrefix("ar") {
            return .ar
        }
        if value.hasPrefix("vi") {
            return .vi
        }
        if value.hasPrefix("th") {
            return .th
        }
        if value.hasPrefix("id") {
            return .id
        }
        if value.hasPrefix("en") {
            return .en
        }
        return nil
    }
}
