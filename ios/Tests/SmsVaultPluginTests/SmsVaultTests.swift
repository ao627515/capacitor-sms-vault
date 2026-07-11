import XCTest
@testable import SmsVaultPlugin

class SmsVaultTests: XCTestCase {
    func testEcho() {
        // Exemple de cas de test fonctionnel pour le plugin.
        // Utilisation de XCTAssert pour vérifier le bon comportement.

        let implementation = SmsVault()
        let value = "Hello, World!"
        let result = implementation.echo(value)

        XCTAssertEqual(value, result)
    }
}
