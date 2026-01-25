<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="UTF-8">
    <title>${subject}</title>
</head>
<body style="font-family: Arial, sans-serif; background-color: #f7f7f7; padding: 24px;">
<table width="100%" cellpadding="0" cellspacing="0">
    <tr>
        <td align="center">
            <table width="600" cellpadding="0" cellspacing="0"
                   style="background-color: #ffffff; padding: 24px; border-radius: 8px;">
                <tr>
                    <td>
                        <h2>${title}</h2>

                        <p>${lead}</p>

                        <p style="margin: 24px 0;">
                            <a href="${inviteUrl}"
                               style="display: inline-block;
                          padding: 12px 20px;
                          background-color: #4f46e5;
                          color: #ffffff;
                          text-decoration: none;
                          border-radius: 6px;">
                                ${cta}
                            </a>
                        </p>

                        <p>${copyLink}</p>

                        <p style="word-break: break-all;">
                            <a href="${inviteUrl}">${inviteUrl}</a>
                        </p>

                        <hr/>

                        <p style="font-size: 12px;">${footer}</p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
