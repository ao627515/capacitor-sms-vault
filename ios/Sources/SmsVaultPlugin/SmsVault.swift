import Foundation

/**
 * Classe stub SmsVault pour iOS.
 * L'accès aux SMS n'est pas autorisé par Apple sur la plateforme iOS.
 * Cette classe est conservée pour assurer la cohérence de la signature du plugin.
 */
@objc public class SmsVault: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
