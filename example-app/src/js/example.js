import { SmsVault } from 'capacitor-sms-vault';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    SmsVault.echo({ value: inputValue })
}
